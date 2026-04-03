package dev.felnull.imp.client.music.netmusic;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.client.music.netmusic.api.ExtraMusicList;
import dev.felnull.imp.client.music.netmusic.api.NetEaseMusic;
import dev.felnull.imp.client.music.netmusic.api.WebApi;
import dev.felnull.imp.client.music.netmusic.api.pojo.NetEaseMusicList;
import dev.felnull.imp.client.music.netmusic.api.pojo.NetEaseMusicSong;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Lyric;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicSource;
import it.unimi.dsi.fastutil.floats.Float2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.floats.Float2ObjectSortedMap;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import oshi.util.tuples.Pair;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetMusicUtil {
    public static final WebApi WEB_API = new NetEaseMusic().getApi();
    public static final Logger LOGGER = LogManager.getLogger(NetMusicUtil.class);
    private static final Gson GSON = new Gson();
    public static URL resolveRedirect(URL originalUrl, int maxRedirects, Map<String, String> headers) throws IOException {
        URL currentUrl = originalUrl;
        HttpURLConnection connection;

        while (maxRedirects-- > 0) {
            connection = (HttpURLConnection) currentUrl.openConnection();
            connection.setInstanceFollowRedirects(false); // 手动处理重定向

            // 设置请求头
            if (headers != null) {
                headers.forEach(connection::setRequestProperty);
            }

            int responseCode = connection.getResponseCode();

            // 如果是 200，直接返回当前 URL
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return currentUrl;
            }

            // 处理 302/301/307/308 等重定向
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 || responseCode == 308) {

                String location = connection.getHeaderField("Location");
                connection.disconnect(); // 关闭当前连接

                if (location == null) {
                    throw new IOException("Redirect with no Location header: " + responseCode);
                }

                // 处理相对路径的 Location
                currentUrl = new URL(currentUrl, location);
            } else {
                throw new IOException("Unexpected HTTP status: " + responseCode);
            }
        }

        throw new IOException("Too many redirects (max: " + maxRedirects + ")");
    }

    public static URL resolveRedirect(URL originalUrl, Map<String, String> headers) throws IOException {
        return resolveRedirect(originalUrl, 10, headers);
    }

    public static URL resolveRedirect(URL originalUrl) throws IOException {
        return resolveRedirect(originalUrl, 10, Map.of());
    }

    public static URL getNetMusicUrl(long id){
        updateCookie();
        final String baseURL = "https://music.163.com/song/media/outer/url?id=%d.mp3";
        String url = String.format(baseURL, id);
        if(!IamMusicPlayer.getConfig().neteaseCookie.isEmpty()){
            var url1 = LoginNeedUtil.pasteVIPUrl(id);
            if(url1 != null)url = url1;
        }
        try{
            return resolveRedirect(URL.of(new URI(url), null), WEB_API.getRequestPropertyData());
        }catch (Exception e){
            return null;
        }
    }

    @Nullable
    public static NetEaseMusicSong.Song getNetMusicSongData(String data){
        try {
            var song = GSON.fromJson(data, NetEaseMusicSong.class);
            return song.getSong();
        } catch (Exception e) {
            LOGGER.debug("获取音乐错误：", e);
            return null;
        }
    }

    @Nullable
    public static String getNetMusicJson(long id){
        updateCookie();
        try {
            return WEB_API.song(id);
        } catch (IOException e) {
            LOGGER.debug("获取音乐错误：", e);
            return null;
        }
    }

    private static void updateCookie(){
        if(IamMusicPlayer.getConfig().neteaseCookie.isEmpty())return;
        var cookie = IamMusicPlayer.getConfig().neteaseCookie;
        if(!cookie.contains("os=pc"))cookie = cookie + ";appver=3.1.6;os=pc";
        WEB_API.getRequestPropertyData().put("Cookie", cookie);
    }

    @SuppressWarnings("all")
    public static URL getIconUrlFromData(String json) throws Exception{
        var data = (Map<String, Object>)GSON.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
        var song = (Map<String, Object>)((List<Object>)data.get("songs")).get(0);
        var album = song.get("album");
        // 大力出奇迹.png
        return new URL((String) ((Map<String, Object>)album).get("picUrl"));
    }

    public static List<Music> getMusicList(long id) throws Exception {
        // 从网络音乐机里拿的代码
        updateCookie();
        var SONGS = new ArrayList<Music>();
        NetEaseMusicList pojo = GSON.fromJson(WEB_API.list(id), NetEaseMusicList.class);
        int count = pojo.getPlayList().getTracks().size();
        int size = Math.min(pojo.getPlayList().getTrackIds().size(), 114514);
        if (count < size) {
            // ids过多时会无法解析，这里分开解析
            long[] ids = new long[size - count];

            for(int i = count; i < size; ++i) {
                ids[i - count] = pojo.getPlayList().getTrackIds().get(i).getId();
            }

            if(ids.length <= 100){
                String extraTrackInfo = WEB_API.songs(ids);
                ExtraMusicList extra = GSON.fromJson(extraTrackInfo, ExtraMusicList.class);
                pojo.getPlayList().getTracks().addAll(extra.getTracks());
            }else{
                int batchSize = 100;
                for(int i = 0; i < ids.length; i += batchSize){
                    int end = Math.min(i + batchSize, ids.length);
                    long[] batchIds = Arrays.copyOfRange(ids, i, end);
                    String extraTrackInfo = WEB_API.songs(batchIds);
                    ExtraMusicList extra = GSON.fromJson(extraTrackInfo, ExtraMusicList.class);
                    pojo.getPlayList().getTracks().addAll(extra.getTracks());
                }
            }
        }
        for(NetEaseMusicList.Track track : pojo.getPlayList().getTracks()) {
            var musicSource = new MusicSource(IMPMusicMedias.NETEASE_MUSIC.getName(), String.valueOf(track.getId()), track.getDuration());
            var name = IamMusicPlayer.getConfig().withTransName && !track.getTransName().isEmpty() ?
                    String.format("%s(%s)", track.getName(), track.getTransName()) :
                    track.getName();

            ImageInfo imageInfo = new ImageInfo(ImageInfo.ImageType.URL, track.getAlbum().getPicUrl());
            SONGS.add(new Music(UUID.randomUUID(),
                    name,
                    String.join("、", track.getArtists()),
                    musicSource,
                    imageInfo,
                    Objects.requireNonNull(Minecraft.getInstance().player).getGameProfile().getId(),
                    track.getPublishTime()));
        }
        return SONGS;
    }

    public static NetEaseMusicList.PlayList getMusicListInfo(long id) throws Exception {
        // 从网络音乐机里拿的代码
        NetEaseMusicList pojo = GSON.fromJson(WEB_API.list(id), NetEaseMusicList.class);
        return pojo.getPlayList();
    }

    @SuppressWarnings("all")
    public static Lyric getLyric(String json){
        var data = (Map<String, Object>)GSON.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
        var lrc = (String)((Map<String, Object>)data.get("lrc")).get("lyric");
        var transformlLrc = "";
        if(data.containsKey("tlyric")){
            transformlLrc = (String)((Map<String, Object>)data.get("tlyric")).get("lyric");
        }
        if(lrc.isEmpty()){
            return null;
        }
        Float2ObjectSortedMap<String> lyricMap;
        Float2ObjectSortedMap<String> transformLyricMap = null;
        if(!transformlLrc.isEmpty()){
            transformLyricMap = new Float2ObjectLinkedOpenHashMap<>();
            for(String part: transformlLrc.split("\n")){
                var p = getLyricPair(part);
                if(p != null){transformLyricMap.put(p.getA(), p.getB());}
            }
        }
        lyricMap = new Float2ObjectLinkedOpenHashMap<>();
        for(String part: lrc.split("\n")){
            var p = getLyricPair(part);
            if(p != null){lyricMap.put(p.getA(), p.getB());}
        }
        return new Lyric(lyricMap, transformLyricMap);
    }

    private static Pair<Float, String> getLyricPair(String input){
        Pattern pattern = Pattern.compile("^\\[(\\d+):(\\d+)[.:](\\d+)](.*)$");
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            int minutes = Integer.parseInt(matcher.group(1));
            int seconds = Integer.parseInt(matcher.group(2));
            int milliseconds = Integer.parseInt(matcher.group(3));
            String text = matcher.group(4);

            // 计算总秒数（带小数）
            float totalSeconds = minutes * 60 + seconds + milliseconds / 1000f;
            return new Pair<>(totalSeconds, text.trim());
        }
        return null;
    }
}
