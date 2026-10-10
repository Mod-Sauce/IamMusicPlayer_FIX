package dev.felnull.imp.client.music.netmusic;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.TrialClip;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.client.music.media.MusicMediaResult;
import dev.felnull.imp.client.music.netmusic.api.ExtraMusicList;
import dev.felnull.imp.client.music.netmusic.api.NetEaseMusic;
import dev.felnull.imp.client.music.netmusic.api.WebApi;
import dev.felnull.imp.client.music.netmusic.api.pojo.NetEaseMusicList;
import dev.felnull.imp.client.music.netmusic.api.pojo.NetEaseMusicSearch;
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
            connection.setInstanceFollowRedirects(false); // handle redirects manually

            // Set the request headers
            if (headers != null) {
                headers.forEach(connection::setRequestProperty);
            }

            int responseCode = connection.getResponseCode();

            // If it is 200, return the current URL directly
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return currentUrl;
            }

            // Handle redirects such as 302/301/307/308
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 || responseCode == 308) {

                String location = connection.getHeaderField("Location");
                connection.disconnect(); // close the current connection

                if (location == null) {
                    throw new IOException("Redirect with no Location header: " + responseCode);
                }

                // Handle a relative-path Location
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
        boolean viaVipApi = false;
        long apiClipMs = -1;
        if(!IamMusicPlayer.getConfig().netMusicConfig.neteaseCookie.isEmpty()){
            var data = LoginNeedUtil.pasteVIPData(id);
            var url1 = urlOf(data);
            if(url1 != null){ url = url1; viaVipApi = true; }
            apiClipMs = clipMsOf(data);
            // [Diagnosis] On a fresh load the decoded length cannot tell a trial apart (a remote track's length is
            // unknown then), so log what the platform answered — that is the only place the truth is visible.
            LinkDebug.log("netease vip data", "id", id, "hasUrl", url1 != null,
                    "time", field(data, "time"), "size", field(data, "size"), "fee", field(data, "fee"),
                    "level", field(data, "level"), "freeTrialInfo", field(data, "freeTrialInfo"),
                    "keys", data == null ? null : data.keySet());
        }
        if (apiClipMs >= 0) TrialClip.remember(NETEASE, String.valueOf(id), apiClipMs);
        else TrialClip.forget(NETEASE, String.valueOf(id));
        try{
            var resolved = resolveRedirect(URL.of(new URI(url), null), WEB_API.getRequestPropertyData());
            // [Diagnosis] "Did the Cookie path answer at all" is *the* question when a track plays only 30 s:
            // without this line the log says nothing about where the audio actually came from.
            LinkDebug.log("netease url", "id", id, "hasCookie", hasCookie(), "viaVipApi", viaVipApi,
                    "apiClipMs", apiClipMs, "final", LinkDebug.safeUrl(resolved.toString()));
            return resolved;
        }catch (Exception e){
            LinkDebug.log("netease url FAIL", "id", id, "hasCookie", hasCookie(), "viaVipApi", viaVipApi,
                    "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /** Loader type of this channel (`MusicSource#getLoaderType`), used as the {@link TrialClip} key half. */
    private static final String NETEASE = "netease";

    @Nullable
    private static Object field(@Nullable java.util.Map<String, Object> data, String key) {
        return data == null ? null : data.get(key);
    }

    /** The {@code url} of a {@code player/url/v1} entry, or null when Netease handed out none. */
    @Nullable
    private static String urlOf(@Nullable java.util.Map<String, Object> data) {
        var u = field(data, "url");
        return u instanceof String s && !s.isEmpty() ? s : null;
    }

    /**
     * How long the address Netease handed out really is, in ms: {@code -1} = the answer says nothing, {@code 0} = a trial flagged without a length.
     */
    private static long clipMsOf(@Nullable java.util.Map<String, Object> data) {
        var t = field(data, "time");
        if (t instanceof Number n && n.longValue() > 0) return n.longValue();
        return field(data, "freeTrialInfo") != null ? 0 : -1;
    }

    /** Where the anonymous address API finally 302s to when Netease "cannot play this one" — used to decide restriction. */
    private static final String RESTRICTED_URL_MARK = "music.163.com/404";

    /** Hard cap for radio import (page guard: if the server ignores offset, without a cap it would keep requesting forever). */
    private static final int MAX_RADIO_PROGRAMS = 5000;

    /**
     * Probe "can this music really fetch audio right now": walks the very address-fetch path used during playback (Cookie included), so the verdict matches playback.
     */
    public static boolean isAudioUnavailable(long id) {
        try {
            // The throwing variant is required: getNetMusicUrl swallows exceptions into null, which would be judged
            // "restricted" and make the UI falsely report "login required" for songs that can play.
            URL url = resolveAudioUrl(id);
            if (url == null)
                return true;
            return url.toString().contains(RESTRICTED_URL_MARK);
        } catch (Exception e) {
            // A failure of the probe itself (offline, timeout, API error) is **not reported**, to avoid false positives
            LOGGER.debug("NetEase availability probe failed: ", e);
            return false;
        }
    }

    /**
     * Same as {@link #getNetMusicUrl} but throwing instead of returning null, so the availability probe can tell "restricted" apart from "probe failed".
     */
    private static URL resolveAudioUrl(long id) throws Exception {
        updateCookie();
        final String baseURL = "https://music.163.com/song/media/outer/url?id=%d.mp3";
        String url = String.format(baseURL, id);
        if (!IamMusicPlayer.getConfig().netMusicConfig.neteaseCookie.isEmpty()) {
            var url1 = urlOf(LoginNeedUtil.pasteVIPData(id));
            if (url1 != null) url = url1;
        }
        var resolved = resolveRedirect(URL.of(new URI(url), null), WEB_API.getRequestPropertyData());
        // Same diagnosis line as `getNetMusicUrl` — this is the variant the availability probe walks.
        LinkDebug.log("netease url (probe)", "id", id, "final", LinkDebug.safeUrl(resolved.toString()));
        return resolved;
    }

    /** Whether a Netease Cookie is configured — used to tell "login required" apart from "login does not help either". */
    public static boolean hasCookie() {
        try {
            return !IamMusicPlayer.getConfig().netMusicConfig.neteaseCookie.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * [Audio-address probe] Conclusion of {@link #probeAudioUrl}: is the address resolved by a platform really audio.
     */
    public enum AudioProbe {
        /** The address answers with audio ⇒ hand it to LavaPlayer as before. */
        OK,
        /** The address answers with a non-audio page (404/HTML) or refuses (4xx/5xx) ⇒ must fail now. */
        NOT_AUDIO,
        /**
         * The CDN **refused the request itself** (401/403/412) — a request-identity problem (headers, risk control, expired sign), **not** a paid/restricted track; must stay distinct from {@code NOT_AUDIO}.
         */
        REJECTED,
        /** The probe itself failed (timeout/DNS/offline) ⇒ **not reported**: an unreachable-here address must not fail a track that may well play. */
        UNKNOWN;
    }

    /**
     * One lightweight probe of a resolved address: GET + {@code Range: bytes=0-1023}, never HEAD (several CDNs answer HEAD 403/405); only an explicit non-audio Content-Type counts, and a 3xx is {@link AudioProbe#UNKNOWN}.
     */
    public static AudioProbe probeAudioUrl(String url) {
        return probeAudioUrl(url, null);
    }

    /**
     * @param headers extra request headers on top of {@code Range} ({@code null} allowed); required wherever the
     *                target CDN refuses a request that does not look like a browser — see above.
     */
    public static AudioProbe probeAudioUrl(String url, @Nullable Map<String, String> headers) {
        if (url == null || url.isBlank())
            return AudioProbe.NOT_AUDIO;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Range", "bytes=0-1023");
            if (headers != null)
                headers.forEach(conn::setRequestProperty);
            // Timeouts kept at the value the other probes in this repo already use (KuGouUtil's 5s): a hung CDN must not stall the loading thread.
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(1000);
            int code = conn.getResponseCode();
            if (code < 200 || code > 299) {
                // 3xx is "unknown" on purpose: see the note above (LavaPlayer's own client may still follow it successfully)
                if (code >= 300 && code < 400)
                    return AudioProbe.UNKNOWN;
                // 401/403/412 = the CDN refused *us*, which is a header/identity problem rather than a bad address
                if (code == 401 || code == 403 || code == 412)
                    return AudioProbe.REJECTED;
                return AudioProbe.NOT_AUDIO;
            }
            var type = conn.getContentType();
            if (type == null || type.isBlank()) {
                // No Content-Type header: read the head of the body instead, since a 404 page served as "audio" often carries no type
                try (var in = conn.getInputStream()) {
                    var head = in.readNBytes(1024);
                    return looksLikeAudioHead(head) ? AudioProbe.OK : AudioProbe.NOT_AUDIO;
                }
            }
            var t = type.toLowerCase(Locale.ROOT);
            if (t.startsWith("audio/") || t.contains("octet-stream"))
                return AudioProbe.OK;
            if (t.contains("html") || t.contains("json") || t.contains("xml") || t.startsWith("text/"))
                return AudioProbe.NOT_AUDIO;
            // Any other type (a video container, a vendor-specific one) is passed through: the decoder is the final judge
            return AudioProbe.OK;
        } catch (Exception e) {
            return AudioProbe.UNKNOWN;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    /**
     * Fallback for a missing Content-Type: an audio/mp4 file starts with {@code ftyp} / {@code ID3} / an MPEG sync word, which an HTML error page does not.
     */
    private static boolean looksLikeAudioHead(byte[] head) {
        var n = head.length;
        for (var i = 0; i + 1 < n; i++) {
            // MP3 frame sync (LavaPlayer hands Netease streams to its MP3 decoder as-is)
            if ((head[i] & 0xFF) == 0xFF && (head[i + 1] & 0xE0) == 0xE0)
                return true;
        }
        var tag = new String(head, 0, Math.min(n, 32), java.nio.charset.StandardCharsets.ISO_8859_1);
        return tag.contains("ftyp") || tag.startsWith("ID3") || tag.startsWith("OggS") || tag.startsWith("fLaC");
    }

    /** A one-line "what the probe saw" for the log; the UI text is supplied by the caller. */
    public static String describeProbe(String url) {
        return "probe not audio | url=" + dev.felnull.imp.client.music.link.LinkDebug.safeUrl(url);
    }

    @Nullable
    public static NetEaseMusicSong.Song getNetMusicSongData(String data){
        try {
            var song = GSON.fromJson(data, NetEaseMusicSong.class);
            return song.getSong();
        } catch (Exception e) {
            LOGGER.debug("Failed to fetch the music: ", e);
            return null;
        }
    }

    @Nullable
    public static String getNetMusicJson(long id){
        updateCookie();
        try {
            return WEB_API.song(id);
        } catch (IOException e) {
            LOGGER.debug("Failed to fetch the music: ", e);
            return null;
        }
    }

    private static void updateCookie(){
        if(IamMusicPlayer.getConfig().netMusicConfig.neteaseCookie.isEmpty())return;
        var cookie = IamMusicPlayer.getConfig().netMusicConfig.neteaseCookie;
        if(!cookie.contains("os=pc"))cookie = cookie + ";appver=3.1.6;os=pc";
        WEB_API.getRequestPropertyData().put("Cookie", cookie);
    }

    @SuppressWarnings("all")
    public static URL getIconUrlFromData(String json) throws Exception{
        var data = (Map<String, Object>)GSON.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
        var song = (Map<String, Object>)((List<Object>)data.get("songs")).get(0);
        var album = song.get("album");
        return new URL((String) ((Map<String, Object>)album).get("picUrl"));
    }

    public static List<Music> getMusicList(long id) throws Exception {
        updateCookie();
        var SONGS = new ArrayList<Music>();
        NetEaseMusicList pojo = GSON.fromJson(WEB_API.list(id), NetEaseMusicList.class);
        int count = pojo.getPlayList().getTracks().size();
        int size = Math.min(pojo.getPlayList().getTrackIds().size(), 114514);
        if (count < size) {
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
            var name = IamMusicPlayer.getConfig().netMusicConfig.withTransName && !track.getTransName().isEmpty() ?
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

    public static List<Music> getMusicsData(List<Long> songIds) throws Exception {
        updateCookie();
        List<Music> SONGS = new ArrayList<>();
        if (songIds == null || songIds.isEmpty())
            return SONGS;
        List<Long> ids = songIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        int batchSize = 100;
        for (int i = 0; i < ids.size(); i += batchSize) {
            int end = Math.min(i + batchSize, ids.size());
            long[] batchIds = new long[end - i];
            for (int j = i; j < end; j++) {
                batchIds[j - i] = ids.get(j);
            }
            String extraTrackInfo = WEB_API.songs(batchIds);
            ExtraMusicList extra = GSON.fromJson(extraTrackInfo, ExtraMusicList.class);
            if (extra == null || extra.getTracks() == null)
                continue;
            for (NetEaseMusicList.Track track : extra.getTracks()) {
                var musicSource = new MusicSource(
                        IMPMusicMedias.NETEASE_MUSIC.getName(),
                        String.valueOf(track.getId()),
                        track.getDuration()
                );
                var name = IamMusicPlayer.getConfig().netMusicConfig.withTransName
                        && track.getTransName() != null
                        && !track.getTransName().isEmpty()
                        ? String.format("%s(%s)", track.getName(), track.getTransName())
                        : track.getName();
                ImageInfo imageInfo = new ImageInfo(
                        ImageInfo.ImageType.URL,
                        track.getAlbum().getPicUrl()
                );
                SONGS.add(new Music(
                        UUID.randomUUID(),
                        name,
                        String.join("、", track.getArtists()),
                        musicSource,
                        imageInfo,
                        Objects.requireNonNull(Minecraft.getInstance().player)
                                .getGameProfile().getId(),
                        track.getPublishTime()
                ));
            }
        }

        return SONGS;
    }

    public static NetEaseMusicList.PlayList getMusicListInfo(long id) throws Exception {
        NetEaseMusicList pojo = GSON.fromJson(WEB_API.list(id), NetEaseMusicList.class);
        return pojo.getPlayList();
    }

    /**
     * Fetch all programs under a "radio / host radio (podcast)".
     */
    public static List<Music> getRadioPrograms(long radioId) throws Exception {
        updateCookie();
        var songs = new ArrayList<Music>();
        int offset = 0;
        int limit = 1000;

        while (true) {
            var url = "https://music.163.com/api/dj/program/byradio?radioId=" + radioId
                    + "&limit=" + limit + "&offset=" + offset;
            var connection = (HttpURLConnection) new URL(url).openConnection();
            WEB_API.getRequestPropertyData().forEach(connection::setRequestProperty);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);

            String body;
            try (var in = connection.getInputStream()) {
                body = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }

            var json = GSON.fromJson(body, JsonObject.class);
            if (json == null || !json.has("programs")) break;
            var programs = json.getAsJsonArray("programs");
            if (programs == null || programs.isEmpty()) break;

            for (var element : programs) {
                if (!element.isJsonObject()) continue;
                // One bad program **must not fail the whole radio import**: toMusicFromProgram calls getAsLong/getAsString
                // on JsonNull for missing fields and throws, so isolating that single entry is enough.
                try {
                    var music = toMusicFromProgram(element.getAsJsonObject());
                    if (music != null)
                        songs.add(music);
                } catch (Exception ex) {
                    LOGGER.debug("Skipping a program that cannot be parsed: ", ex);
                }
            }

            boolean more = json.has("more") && json.get("more").getAsBoolean();
            // Advance by the number the server actually returned, not by "how many I requested": a shortened page
            // would break early with silent gaps, and adding `limit` on top skipped a whole page.
            offset += programs.size();
            if (!more || programs.isEmpty() || offset > MAX_RADIO_PROGRAMS) break;
        }

        return songs;
    }

    /**
     * Fetch a **single program** ({@code program?id=<program id>} / {@code dj?id=<program id>}) and turn it into one music entry.
     */
    @Nullable
    public static Music getProgram(long programId) throws Exception {
        updateCookie();
        var url = "https://music.163.com/api/dj/program/detail?id=" + programId;
        var connection = (HttpURLConnection) new URL(url).openConnection();
        WEB_API.getRequestPropertyData().forEach(connection::setRequestProperty);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);

        String body;
        try (var in = connection.getInputStream()) {
            body = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        var json = GSON.fromJson(body, JsonObject.class);
        if (json == null || !json.has("program") || !json.get("program").isJsonObject())
            return null;

        return toMusicFromProgram(json.getAsJsonObject("program"));
    }

    /**
     * Converts one Netease "program" into a Music: its {@code mainSong} (the podcast audio) yields a real address through {@code song/media/outer/url}, so it plays right after import.
     */
    @Nullable
    private static Music toMusicFromProgram(JsonObject program) {
        if (!program.has("mainSong") || !program.get("mainSong").isJsonObject())
            return null;
        var song = program.getAsJsonObject("mainSong");

        // Field reads must all guard against JsonNull: `has("x")` returns **true** for `"x": null`,
        // and getAsLong/getAsString then throw UnsupportedOperationException (one bad field ruins the whole import).
        long songId = jsonLong(song, "id", 0);
        if (songId <= 0)
            return null;

        var name = jsonStr(song, "name", "");
        long duration = jsonLong(song, "duration", 0);

        // Author: prefer the audio's own artists, falling back to the host the program belongs to
        var author = "";
        if (song.has("artists") && song.get("artists").isJsonArray()) {
            var names = new ArrayList<String>();
            for (var artist : song.getAsJsonArray("artists")) {
                if (artist.isJsonObject()) {
                    var n = jsonStr(artist.getAsJsonObject(), "name", "");
                    if (!n.isEmpty())
                        names.add(n);
                }
            }
            author = String.join("、", names);
        }
        if (author.isEmpty() && program.has("dj") && program.get("dj").isJsonObject()) {
            author = jsonStr(program.getAsJsonObject("dj"), "nickname", "");
        }

        String picUrl = null;
        if (program.has("coverUrl") && !program.get("coverUrl").isJsonNull())
            picUrl = program.get("coverUrl").getAsString();

        var musicSource = new MusicSource(IMPMusicMedias.NETEASE_MUSIC.getName(),
                String.valueOf(songId), duration);
        var imageInfo = (picUrl == null || picUrl.isEmpty())
                ? ImageInfo.EMPTY
                : new ImageInfo(ImageInfo.ImageType.URL, picUrl);

        return new Music(UUID.randomUUID(),
                name,
                author,
                musicSource,
                imageInfo,
                Objects.requireNonNull(Minecraft.getInstance().player).getGameProfile().getId(),
                System.currentTimeMillis());
    }

    /**
     * Read a string field, returning {@code def} when the field is missing or the value is {@code null}.
     */
    private static String jsonStr(JsonObject json, String key, String def) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull())
            return def;
        try {
            return json.get(key).getAsString();
        } catch (Exception e) {
            return def;
        }
    }

    /** Read a long field, returning {@code def} when the field is missing / {@code null} / of the wrong type (reasoning in {@link #jsonStr}). */
    private static long jsonLong(JsonObject json, String key, long def) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull())
            return def;
        try {
            return json.get(key).getAsLong();
        } catch (Exception e) {
            return def;
        }
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
        // [offset:±N] in the LRC header shifts every timestamp (ms). The translation carries no offset of its own,
        // so it inherits the original's — the two maps must keep identical keys (getPart looks up by the original's time).
        int offsetMillis = Lyric.getLrcOffsetMillis(lrc, 0);
        int transOffsetMillis = Lyric.getLrcOffsetMillis(transformlLrc, offsetMillis);
        // [credit-line filter] Drop production-crew / copyright lines ("作词 : 黄家驹"); a line without an mm:ss
        // stamp ([ti:]/[ar:]/[offset:]) never reaches here.
        boolean filterCredits = Lyric.isCreditLineFilterEnabled();
        Float2ObjectSortedMap<String> lyricMap;
        Float2ObjectSortedMap<String> transformLyricMap = null;
        if(!transformlLrc.isEmpty()){
            transformLyricMap = new Float2ObjectLinkedOpenHashMap<>();
            for(String part: transformlLrc.split("\n")){
                var p = getLyricPair(part);
                if(p != null && !(filterCredits && Lyric.isCreditLine(p.getB()))){
                    transformLyricMap.put(Lyric.applyLrcOffset(p.getA(), transOffsetMillis), p.getB());
                }
            }
        }
        lyricMap = new Float2ObjectLinkedOpenHashMap<>();
        for(String part: lrc.split("\n")){
            var p = getLyricPair(part);
            if(p != null && !(filterCredits && Lyric.isCreditLine(p.getB()))){
                lyricMap.put(Lyric.applyLrcOffset(p.getA(), offsetMillis), p.getB());
            }
        }
        // 逐字（yrc）：行表与译文都不动，只多挂一份"每行的词"；没有 yrc 时返回 null = 原来的整行行为
        var wordLyrics = NetEaseYrc.attach(yrcOf(data), offsetMillis, lyricMap);
        return new Lyric(lyricMap, transformLyricMap, wordLyrics);
    }

    /** {@code yrc} 字段（形如 {@code {"lyric": "..."}}）；缺字段 / 结构不符 / 空串时返回 null。 */
    @SuppressWarnings("all")
    private static String yrcOf(Map<String, Object> data) {
        if (!(data.get("yrc") instanceof Map))
            return null;
        var lyric = ((Map<String, Object>) data.get("yrc")).get("lyric");
        return lyric instanceof String s && !s.isEmpty() ? s : null;
    }

    private static Pair<Float, String> getLyricPair(String input){
        Pattern pattern = Pattern.compile("^\\[(\\d+):(\\d+)[.:](\\d+)](.*)$");
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            int minutes = Integer.parseInt(matcher.group(1));
            int seconds = Integer.parseInt(matcher.group(2));
            int milliseconds = Integer.parseInt(matcher.group(3));
            String text = matcher.group(4);

            // compute the total seconds (with fraction)
            float totalSeconds = minutes * 60 + seconds + milliseconds / 1000f;
            return new Pair<>(totalSeconds, text.trim());
        }
        return null;
    }

    public static List<MusicMediaResult> search(String keyword){
        NetEaseMusicSearch search;
        try {
            search = GSON.fromJson(WEB_API.search(keyword), NetEaseMusicSearch.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
        if(search.getSongs().isEmpty())return Collections.emptyList();
        var ids = new long[search.getSongs().size()];
        List<NetEaseMusicSearch.Song> songs = search.getSongs();
        for (int i = 0, songsSize = songs.size(); i < songsSize; i++) {
            NetEaseMusicSearch.Song song = songs.get(i);
            ids[i] = song.getId();
        }
        ExtraMusicList extra;
        try{
            extra = GSON.fromJson(WEB_API.songs(ids), ExtraMusicList.class);
        }catch (Exception e){
            return Collections.emptyList();
        }
        var result = new ArrayList<MusicMediaResult>();
        for (NetEaseMusicList.Track track: extra.getTracks()){
            var name = IamMusicPlayer.getConfig().netMusicConfig.withTransName && !track.getTransName().isEmpty() ?
                    String.format("%s(%s)", track.getName(), track.getTransName()) :
                    track.getName();
            var author = String.join("、", track.getArtists());
            var source = new MusicSource(IMPMusicMedias.NETEASE_MUSIC.getName(), String.valueOf(track.getId()), track.getDuration());
            var image = new ImageInfo(ImageInfo.ImageType.URL, track.getAlbum().getPicUrl());
            result.add(new MusicMediaResult(source, image, name, author));
        }
        return result;
    }
}
