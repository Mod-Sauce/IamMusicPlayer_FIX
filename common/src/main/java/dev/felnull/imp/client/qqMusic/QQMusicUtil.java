package dev.felnull.imp.client.qqMusic;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.link.LinkDebug;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class QQMusicUtil {
    public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    public static final Gson GSON = new Gson();
    public static final URI MUSIC_U_URL = URI.create("https://u.y.qq.com/cgi-bin/musicu.fcg");
    /**
     * QQ's dynamic-lyric ("QRC", per-word) endpoint: the only route that yields per-word timings and the translation line, which the {@code lyric} field of the song-detail API lacks. Payload format: {@link QrcCodec}.
     */
    public static final URI QRC_LYRIC_URL = URI.create("https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg");
    public static final Duration TIMEOUT = Duration.ofSeconds(5);
    public static final Logger LOGGER = LogManager.getLogger(QQMusicUtil.class);

    public static String getCookie(){
        return IamMusicPlayer.getConfig().QQMusicConfig.QQMusicCookie;
    }

    /** Whether a QQ Music Cookie is configured. */
    public static boolean hasCookie(){
        var c = getCookie();
        return c != null && !c.isEmpty();
    }

    private record CachedIds(String mid, String mediaMid) {
    }

    /**
     * Small songId → ids cache so the availability probe right after the load phase can reuse the details it just fetched (saves one request). Bounded, can be refetched anytime.
     */
    private static final java.util.Map<Long, CachedIds> idsCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static void cacheIds(long songId, String mid, String mediaMid){
        if (mid == null || mid.isEmpty()) return;
        if (idsCache.size() > 256) idsCache.clear();
        idsCache.put(songId, new CachedIds(mid, mediaMid));
    }

    /**
     * "Shell address" check: when QQ cannot get a playback address it returns {@code sip + ""}, which concatenates into a domain with no path — not audio, and handing it to the player only yields a playback error.
     */
    public static boolean isShellUrl(String url){
        if (url == null || url.isEmpty()) return true;
        try {
            var path = URI.create(url).getPath();
            return path == null || path.isEmpty() || "/".equals(path);
        } catch (Exception e) {
            return true;
        }
    }

    /** Probe result: whether this route (including the Cookie from config) can really fetch audio right now. */
    public enum AudioStatus {
        /** Audio can be fetched. **This is possible with or without a Cookie** — QQ does not "require a Cookie", see the note on probeAudioStatus. */
        OK,
        /** Audio cannot be fetched: without a Cookie it is "this one is not given anonymously", with a Cookie it is an expired/incomplete Cookie or a restricted track. */
        UNAVAILABLE
    }

    /** Real probe of the playback chain ({@code getSongData} + {@code getMusicUrl}), even with no Cookie: anonymous
     * requests yield a real purl, so judging them dead puts a false "Cookie required" notice on playable songs. */
    public static AudioStatus probeAudioStatus(long songId){
        try {
            var cached = idsCache.get(songId);
            var mid = cached == null ? null : cached.mid();
            var mediaMid = cached == null ? null : cached.mediaMid();
            if (mid == null) {
                var data = getSongData(songId);
                mid = data == null ? null : data.getMID();
                mediaMid = data == null ? null : data.getMediaMID();
            }
            if (mid == null || mid.isEmpty())
                return AudioStatus.UNAVAILABLE;
            var url = getMusicUrl(mid, mediaMid);
            var ok = !isShellUrl(url);
            // The address must be redacted: QQ's playback address puts the signature in the query string (?vkey=…), and logging it in full would leak a credential.
            LinkDebug.log("QQ audio-probe", "id", songId, "mid", mid, "url", LinkDebug.safeUrl(url), "ok", ok,
                    "hasCookie", hasCookie());
            return ok ? AudioStatus.OK : AudioStatus.UNAVAILABLE;
        } catch (Exception e) {
            LinkDebug.log("QQ audio-probe EX", "id", songId,
                    "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
            return AudioStatus.UNAVAILABLE;
        }
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
            if(result.statusCode() != 200){
                LinkDebug.log("QQ getSongData", "id", musicID, "httpStatus", result.statusCode());
                return null;
            }
            var parsed = GSON.fromJson(result.body(), QQMusicData.class);
            if (parsed != null)
                cacheIds(musicID, parsed.getMID(), parsed.getMediaMID());
            LinkDebug.log("QQ getSongData", "id", musicID, "httpStatus", 200,
                    "name", parsed == null ? null : parsed.getName(),
                    "mid", parsed == null ? null : parsed.getMID(),
                    "second", parsed == null ? null : parsed.getSecond(),
                    "cookieEmpty", getCookie() == null || getCookie().isEmpty(),
                    "bodyHead", result.body() == null ? null
                            : result.body().substring(0, Math.min(160, result.body().length())));
            return parsed;
        }catch (Exception e){
            LinkDebug.log("QQ getSongData EX", "id", musicID,
                    "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
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
        return getMusicUrl(musicMID, null);
    }

    /**
     * @param mediaMID {@code track_info.file.media_mid} (not the song mid) for the file-name chain; null → song mid.
     */
    @Nullable
    public static String getMusicUrl(String musicMID, @Nullable String mediaMID){
        try {
            var data = buildSongURLData(musicMID, mediaMID);
            var request = HttpRequest.newBuilder(MUSIC_U_URL)
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(data)))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://y.qq.com/")
                    .header("Content-Type", "application/json")
                    .header("Cookie", getCookie())
                    .timeout(TIMEOUT)
                    .build();
            var result = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if(result.statusCode() != 200){
                LinkDebug.log("QQ getMusicUrl", "mid", musicMID, "httpStatus", result.statusCode());
                return null;
            }
            var r = GSON.fromJson(result.body(), QQMusicURLData.class);
            var url = r == null ? null : r.getUrl();
            // Diagnostics: a null url here usually means copyright/preview restrictions or a missing cookie.
            // ⚠️ The address must always be redacted (it carries a ?vkey= signature), never written into the log in full.
            LinkDebug.log("QQ getMusicUrl", "mid", musicMID,
                    "url", LinkDebug.safeUrl(url),
                    "cookieEmpty", getCookie() == null || getCookie().isEmpty());
            return url;
        }catch (Exception e){
            LinkDebug.log("QQ getMusicUrl EX", "mid", musicMID,
                    "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
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

    /** Same as above, but keyed by {@code song_mid}: a mid passed as song_id returns songinfo.code = 400. */
    public static JsonElement buildSongInfoDataByMid(String musicMID){
        var songInfo = new JsonObject();
        songInfo.addProperty("module", "music.pf_song_detail_svr");
        songInfo.addProperty("method", "get_song_detail");
        var param = new JsonObject();
        param.addProperty("song_mid", musicMID);
        songInfo.add("param", param);
        return buildRequestData("songinfo", songInfo);
    }

    /** Playable file names, best quality first: the order **is** the fallback chain, so the first non-empty purl wins.
     * {@code RS02} is deliberately missing — it is a 60 s preview, which stalls at 22% instead of "needs a Cookie". */
    private static final String[][] QUALITY_CHAIN = {
            {"F000", "flac"}, {"M800", "mp3"}, {"M500", "mp3"},
            {"C600", "m4a"}, {"C400", "m4a"}, {"C200", "m4a"}, {"C100", "m4a"},
    };

    public static JsonElement buildSongURLData(String musicMID){
        return buildSongURLData(musicMID, null);
    }

    /** @param mediaMID see {@link #getMusicUrl(String, String)} */
    public static JsonElement buildSongURLData(String musicMID, @Nullable String mediaMID){
        return buildSongURLData(musicMID, mediaMID, IamMusicPlayer.getConfig().QQMusicConfig.QQMusicQuality);
    }

    /**
     * @param mediaMID see {@link #getMusicUrl(String, String)}
     * @param quality  {@code 320} skips lossless, {@code 128} = standard file only, else the chain starts at the top;
     *                 taken as a parameter since the game config cannot be initialised outside the game. */
    public static JsonElement buildSongURLData(String musicMID, @Nullable String mediaMID, @Nullable String quality){
        var start = qualityStart(quality);
        var base = mediaMID == null || mediaMID.isEmpty() ? musicMID : mediaMID;
        var fileNames = new JsonArray();
        var mids = new JsonArray();
        var songTypes = new JsonArray();
        for (int i = start; i < QUALITY_CHAIN.length; i++) {
            fileNames.add(QUALITY_CHAIN[i][0] + base + "." + QUALITY_CHAIN[i][1]);
            mids.add(musicMID);
            songTypes.add(0);
        }
        // Last resort: if the media version named by media_mid is gone, a name built from the song mid still plays.
        if (!base.equals(musicMID)) {
            fileNames.add("C400" + musicMID + ".m4a");
            mids.add(musicMID);
            songTypes.add(0);
        }

        var req = new JsonObject();
        req.addProperty("module", "vkey.GetVkeyServer");
        req.addProperty("method", "CgiGetVkey");
        var param = new JsonObject();
        param.addProperty("guid", String.valueOf(System.currentTimeMillis()));
        param.add("filename", fileNames);
        param.add("songmid", mids);
        param.add("songtype", songTypes);
        param.addProperty("uin", "0");
        param.addProperty("loginflag", 1);
        param.addProperty("platform", "20");
        req.add("param", param);
        return buildRequestData("req_0", req);
    }

    /** Index into {@link #QUALITY_CHAIN} the chain starts at, see {@link #buildSongURLData(String, String, String)}. */
    static int qualityStart(@Nullable String quality){
        if (quality == null) return 0;
        return switch (quality.trim()) {
            case "320" -> 1;
            case "128" -> 4;
            default -> 0;
        };
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

    /** Converts a songmid (e.g. {@code 0039MnYb0qxYhV}) to the numeric songid that {@link #getSongData(long)} takes,
     * via the same API with the parameter renamed {@code song_mid} (share links give the mid, not the id). */
    @Nullable
    public static Long getSongIdFromMid(String musicMID) {
        if (musicMID == null || musicMID.isEmpty())
            return null;

        try {
            var data = buildSongInfoDataByMid(musicMID);
            var request = HttpRequest.newBuilder(MUSIC_U_URL)
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(data)))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://y.qq.com/")
                    .header("Content-Type", "application/json")
                    .header("Cookie", getCookie())
                    .timeout(TIMEOUT)
                    .build();
            var result = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (result.statusCode() != 200) return null;

            var json = GSON.fromJson(result.body(), JsonObject.class);
            if (!json.has("songinfo") || !json.getAsJsonObject("songinfo").has("data")) return null;

            var dataObj = json.getAsJsonObject("songinfo").getAsJsonObject("data");
            if (!dataObj.has("track_info")) return null;

            var trackInfo = dataObj.getAsJsonObject("track_info");
            if (!trackInfo.has("id")) return null;

            long id = trackInfo.get("id").getAsLong();
            return id > 0 ? id : null;
        } catch (Exception e) {
            LOGGER.debug("Error:", e);
        }
        return null;
    }

    public static @Nullable MusicMediaResult getMusicMediaResult(String id){
        long musicID;
        try{
            musicID = Long.parseLong(id);
        }catch (NumberFormatException e) {
            // A share link gives the songmid, so convert it to a numeric songid first (if that fails, treat it as unsupported)
            var fromMid = getSongIdFromMid(id);
            if (fromMid == null) return null;
            musicID = fromMid;
        }
        var data = getSongData(musicID);
        if(data == null)return null;

        // [Duration] Unavailable or zero means the source must not be created: duration 0 makes the player report "finished" on the first tick and destroy the slot, i.e. silent playback.
        // Restricted/VIP tracks report interval 0 when not logged in or with an incomplete Cookie.
        var second = data.getSecond();
        if (second == null || second <= 0) {
            LinkDebug.log("QQ getMusicMediaResult SKIP", "songId", musicID, "name", data.getName(),
                    "second", second, "reason", "no duration（interval 空/0）");
            return null;
        }

        ImageInfo img;
        if(data.getPicUrl() == null || data.getPicUrl().isEmpty())
            img = ImageInfo.EMPTY;
        else img = new ImageInfo(ImageInfo.ImageType.URL, data.getPicUrl());
        return new MusicMediaResult(
                new MusicSource(
                        "qq_music",
                        // Store the parsed numeric songid: playback (QQMusicAudioSourceManager) and lyrics (getLyric) only accept a numeric id,
                        // storing the mid would break both routes
                        String.valueOf(musicID),
                        second * 1000
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

        // [QRC first] Per-word lyrics (karaoke in the world renderer) plus the translation line only come from the
        // QRC route; the song-detail `lyric` field is plain LRC. Any failure on that route (network / non-200 /
        // no payload / decryption or parse error / 0 lines) falls through to the LRC route below, unchanged.
        var qrc = getQrcLyric(musicID);
        if (qrc != null)
            return qrc;

        var data = getSongData(musicID);
        if(data == null)return null;
        var lyric = data.getLyric();
        if(lyric == null)return Lyric.EMPTY;
        return lyric;
    }

    /** {@code POST lyric_download.fcg} (version=15&miniversion=82&lrctype=4&musicid=<numeric id>) → CDATA hex →
     * {@link QrcCodec#decrypt(String)} → {@link QrcCodec#parse(String, long)}; {@code null} on any failure, even
     * "parsed to 0 lines", so the caller keeps the LRC route; one {@code QQ qrc} diagnostic line. */
    @Nullable
    private static Lyric getQrcLyric(long musicID){
        int httpStatus = -1;
        int cipherBytes = 0;
        Lyric lyric = null;
        try {
            var request = HttpRequest.newBuilder(QRC_LYRIC_URL)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "version=15&miniversion=82&lrctype=4&musicid=" + musicID))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://y.qq.com/")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(TIMEOUT)
                    .build();
            var result = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            httpStatus = result.statusCode();
            if (httpStatus == 200) {
                var hex = QrcCodec.extractCipherHex(result.body());
                if (hex != null) {
                    cipherBytes = hex.length() / 2;
                    // fallbackDurationMs = 0 (unknown): the QRC route must not spend the song-detail request first,
                    // so the parser is left without a song length and invents nothing at the tail.
                    lyric = QrcCodec.parse(QrcCodec.decrypt(hex), 0L);
                }
            }
        } catch (Exception e) {
            LinkDebug.log("QQ qrc EX", "musicid", musicID,
                    "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        var dto = Lyric.toDTO(lyric);           // null when nothing was parsed at all
        var lines = dto == null ? 0 : dto.lyrics.size();
        var words = 0;
        if (dto != null && dto.wordLyrics != null)
            for (var line : dto.wordLyrics)
                words += line.words.size();
        LinkDebug.log("QQ qrc", "musicid", musicID, "httpStatus", httpStatus, "cipherBytes", cipherBytes,
                "lines", lines, "words", words, "fallback", lines == 0);
        // 0 lines counts as a failed QRC route: the LRC route still has a chance (and stays the behaviour of record).
        return lines == 0 ? null : lyric;
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
