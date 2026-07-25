package dev.felnull.imp.client.qqMusic;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.loader.IMPMusicLoaders;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.client.music.media.MusicMediaResult;
import dev.felnull.imp.client.qqMusic.resource.QQMusicData;
import dev.felnull.imp.client.qqMusic.resource.QQMusicURLData;
import dev.felnull.imp.client.qqMusic.resource.QQPlaylistData;
import dev.felnull.imp.client.qqMusic.resource.QQSearchData;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Lyric;
import dev.felnull.imp.music.resource.MusicSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class QQMusicUtil {
    public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    public static final Gson GSON = new Gson();
    public static final URI MUSIC_U_URL = URI.create("https://u.y.qq.com/cgi-bin/musicu.fcg");
    public static final Duration TIMEOUT = Duration.ofSeconds(5);
    public static final Logger LOGGER = LogManager.getLogger(QQMusicUtil.class);

    public static String getCookie(){
        return IamMusicPlayer.getConfig().QQMusicConfig.QQMusicCookie;
    }

    @Nullable
    public static QQMusicData getSongData(long musicID){
        try {
            var data = buildSongInfoData(musicID);
            var request = HttpRequest.newBuilder(MUSIC_U_URL)
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(data)))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://y.qq.com/")
                    .header("Content-Type", "application/json")
                    .header("Cookie", getCookie())
                    .timeout(TIMEOUT)
                    .build();
            var result = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if(result.statusCode() != 200)return null;
            return GSON.fromJson(result.body(), QQMusicData.class);
        }catch (Exception e){
            LOGGER.debug("Error:", e);
        }
        return null;
    }

    @Nullable
    public static QQPlaylistData getPlaylistData(long listID){
        try {
            var data = buildPlaylistData(listID);
            var request = HttpRequest.newBuilder(MUSIC_U_URL)
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(data)))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://y.qq.com/")
                    .header("Content-Type", "application/json")
                    .header("Cookie", getCookie())
                    .timeout(TIMEOUT)
                    .build();
            var result = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if(result.statusCode() != 200)return null;
            return GSON.fromJson(result.body(), QQPlaylistData.class);
        }catch (Exception e){
            LOGGER.debug("Error:", e);
        }
        return null;
    }

    @Nullable
    public static String getMusicUrl(String musicMID){
        try {
            var data = buildSongURLData(musicMID);
            var request = HttpRequest.newBuilder(MUSIC_U_URL)
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(data)))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://y.qq.com/")
                    .header("Content-Type", "application/json")
                    .header("Cookie", getCookie())
                    .timeout(TIMEOUT)
                    .build();
            var result = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if(result.statusCode() != 200)return null;
            var r = GSON.fromJson(result.body(), QQMusicURLData.class);
            return r.getUrl();
        }catch (Exception e){
            LOGGER.debug("Error:", e);
        }
        return null;
    }

    public static JsonElement buildRequestData(String moduleName, JsonElement module){
        var result = new JsonObject();
        var common = new JsonObject();
        common.addProperty("ct", 24);
        common.addProperty("cv", 4747474);
        common.addProperty("guid", String.valueOf(System.currentTimeMillis()));
        common.addProperty("format", "json");
        common.addProperty("inCharset", "utf-8");
        common.addProperty("outCharset", "utf-8");
        common.addProperty("notice", 0);
        common.addProperty("platform", "yqq.json");
        common.addProperty("needNewCode", 1);
        common.addProperty("uin", "0");
        result.add("comm", common);
        result.add(moduleName, module);
        return result;
    }

    public static JsonElement buildSearchData(String keyword) {
        var search = new JsonObject();
        search.addProperty("module", "music.search.SearchCgiService");
        search.addProperty("method", "DoSearchForQQMusicDesktop");

        var param = new JsonObject();
        param.addProperty("query", keyword);
        param.addProperty("search_type", 0);
        param.addProperty("page_num", 1);
        param.addProperty("num_per_page", 30);
        param.addProperty("remoteplace", "txt.yqq.top");

        search.add("param", param);

        return buildRequestData("req_0", search);
    }

    public static JsonElement buildSongInfoData(long musicID){
        var songInfo = new JsonObject();
        songInfo.addProperty("module", "music.pf_song_detail_svr");
        songInfo.addProperty("method", "get_song_detail");
        var param = new JsonObject();
        param.addProperty("song_id", musicID);
        songInfo.add("param", param);
        return buildRequestData("songinfo", songInfo);
    }

    public static JsonElement buildSongURLData(String musicMID){
        var req = new JsonObject();
        req.addProperty("module", "vkey.GetVkeyServer");
        req.addProperty("method", "CgiGetVkey");
        var param = new JsonObject();
        param.addProperty("guid", String.valueOf(System.currentTimeMillis()));
        var mid = new JsonArray();
        mid.add(musicMID);
        param.add("songmid", mid);
        var songType = new JsonArray();
        songType.add(0);
        param.add("songtype", songType);
        param.addProperty("uin", "0");
        param.addProperty("loginflag", 1);
        param.addProperty("platform", "20");
        req.add("param", param);
        return buildRequestData("req_0", req);
    }

    public static JsonElement buildPlaylistData(long listID) {
        var playlist = new JsonObject();
        playlist.addProperty("module", "music.srfDissInfo.aiDissInfo");
        playlist.addProperty("method", "uniform_get_Dissinfo");
        var param = new JsonObject();
        param.addProperty("disstid", listID);
        param.addProperty("userinfo", 1);
        param.addProperty("tag", 1);
        playlist.add("param", param);
        return buildRequestData("req_0", playlist);
    }

    public static @Nullable MusicMediaResult getMusicMediaResult(String id){
        long musicID;
        try{
            musicID = Long.parseLong(id);
        }catch (NumberFormatException e) {
            return null;
        }
        var data = getSongData(musicID);
        if(data == null)return null;
        if(data.getSecond() == null)return null;
        ImageInfo img;
        if(data.getPicUrl() == null && data.getPicUrl().isEmpty())
            img = ImageInfo.EMPTY;
        else img = new ImageInfo(ImageInfo.ImageType.URL, data.getPicUrl());
        return new MusicMediaResult(
                new MusicSource(
                        "qq_music",
                        id,
                        data.getSecond() * 1000
                ),
                img,
                data.getName(),
                data.getSingerString()
        );
    }

    public static Lyric getLyric(String id){
        long musicID;
        try{
            musicID = Long.parseLong(id.replace("qq_music:", ""));
        }catch (NumberFormatException e) {
            return null;
        }
        var data = getSongData(musicID);
        if(data == null)return null;
        var lyric = data.getLyric();
        if(lyric == null)return Lyric.EMPTY;
        return lyric;
    }

    public static QQSearchData search(String keyword) {
        try {
            var data = buildSearchData(keyword);

            var request = HttpRequest.newBuilder(MUSIC_U_URL)
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(data)))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://y.qq.com/")
                    .header("Content-Type", "application/json")
                    .header("Cookie", getCookie())
                    .timeout(TIMEOUT)
                    .build();

            var result = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            return GSON.fromJson(result.body(), QQSearchData.class);

        } catch (Exception e) {
            LOGGER.debug("Error:", e);
        }
        return null;
    }

    public static List<MusicMediaResult> searchMusicResult(String keyword){
        if(keyword.isEmpty())return List.of();
        var search = search(keyword);
        if(search == null)return List.of();
        return search.getSongs().stream().map(
                info -> {
                    ImageInfo img;
                    if(info.getPicUrl() == null)
                        img = ImageInfo.EMPTY;
                    else img = new ImageInfo(ImageInfo.ImageType.URL, info.getPicUrl());
                    return new MusicMediaResult(
                            new MusicSource(IMPMusicMedias.QQ_MUSIC.getName(), String.valueOf(info.id), info.length),
                            img,
                            info.name,
                            info.getSingerString()
                    );
                }
        ).toList();
    }
}
