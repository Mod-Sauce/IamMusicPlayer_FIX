package dev.felnull.imp.client.kugou;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.client.music.media.MusicMediaResult;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Lyric;
import dev.felnull.imp.music.resource.MusicSource;
import it.unimi.dsi.fastutil.floats.Float2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.floats.Float2ObjectSortedMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * KuGou single-track fetch over the **public API** ({@code getSongInfo.php?cmd=playInfo}) — no signature / device /
 * login; it needs the **Android UA** and {@code Referer: https://m.kugou.com/}, and the link **expires** (never store it).
 */
public class KuGouUtil {
    private static final Logger LOGGER = LogManager.getLogger(KuGouUtil.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    // connectTimeout is required too: a JDK HttpClient's request timeout does not cover the connect phase, so a
    // hung connect would never fail.
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    /** KuGou identifier = 32-hex-digit hash (case insensitive, always sent to the API in lowercase). */
    public static final Pattern HASH_PATTERN = Pattern.compile("[0-9A-Fa-f]{32}");

    /** The data API requires the Android UA (a desktop UA gets no url). */
    private static final String ANDROID_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36";
    private static final String API = "http://m.kugou.com/app/i/getSongInfo.php";

    /** Cover URLs carry the {@code {size}} template placeholder, replaced with the actual size. */
    private static final String COVER_SIZE = "400";

    private KuGouUtil() {
    }

    /** Whether this is a valid KuGou hash (32 hex digits). */
    public static boolean isHash(String s) {
        return s != null && HASH_PATTERN.matcher(s).matches();
    }

    /** Normalizes to the lowercase form the API wants; null when invalid. */
    @Nullable
    public static String normalizeHash(@Nullable String s) {
        if (s == null)
            return null;
        var t = s.trim();
        return isHash(t) ? t.toLowerCase() : null;
    }

    /**
     * Fetches the details of one music (including the audio address **as of now**).
     */
    @Nullable
    public static KuGouSongInfo getSongInfo(String hash) {
        var h = normalizeHash(hash);
        if (h == null) {
            LinkDebug.log("KuGou getSongInfo SKIP", "hash", hash, "reason", "not a 32-hex hash");
            return null;
        }
        try {
            var request = HttpRequest.newBuilder(URI.create(API + "?cmd=playInfo&hash=" + h + "&quality=128"))
                    .header("User-Agent", ANDROID_UA)
                    .header("Referer", "https://m.kugou.com/")
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            var result = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (result.statusCode() != 200) {
                LinkDebug.log("KuGou getSongInfo", "hash", h, "httpStatus", result.statusCode());
                return null;
            }

            var json = JsonParser.parseString(result.body()).getAsJsonObject();
            var info = parse(h, json);
            // Diagnostics: the error field states the reason directly (e.g. "payment required")
            LinkDebug.log("KuGou getSongInfo", "hash", h,
                    "name", info.name(),
                    "author", info.author(),
                    "second", info.durationMs() > 0 ? info.durationMs() / 1000 : null,
                    "hasUrl", info.audioUrl() != null,
                    "error", info.error(),
                    "cover", info.coverUrl());
            return info;
        } catch (Exception e) {
            LinkDebug.log("KuGou getSongInfo EX", "hash", h,
                    "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
            LOGGER.debug("Error:", e);
            return null;
        }
    }

    // ---------------- search / playlist (both use mobilecdn public APIs, usable anonymously) ----------------
    // mobilecdn.kugou.com only works over **http**: under https its certificate does not match the domain.
    private static final String MOBILECDN = "http://mobilecdn.kugou.com/api/v3";

    /**
     * Searches music. The results carry no cover — that is fetched by {@link #getSongInfo} when the music is loaded.
     */
    public static List<KuGouSongInfo> search(String keyword, int page, int pageSize) {
        if (keyword == null || keyword.isBlank())
            return List.of();
        var url = MOBILECDN + "/search/song?format=json&keyword=" + enc(keyword)
                + "&page=" + Math.max(1, page) + "&pagesize=" + Math.max(1, pageSize) + "&showtype=1";
        var json = getJson(url);
        if (json == null)
            return List.of();

        var out = new ArrayList<KuGouSongInfo>();
        try {
            var info = json.getAsJsonObject("data").getAsJsonArray("info");
            for (var e : info) {
                var o = e.getAsJsonObject();
                var hash = normalizeHash(str(o, "hash"));
                if (hash == null)
                    continue;
                out.add(new KuGouSongInfo(hash, str(o, "songname"), str(o, "singername"),
                        secondsToMs(str(o, "duration")), null, null, null));
            }
        } catch (Exception e) {
            LinkDebug.log("KuGou search EX", "keyword", keyword,
                    "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        LinkDebug.log("KuGou search", "keyword", keyword, "page", page, "count", out.size());
        return out;
    }

    /** Fetches a KuGou "playlist/collection"; links look like {@code www.kugou.com/yy/special/single/<id>.html}. */
    @Nullable
    public static KuGouPlaylist getPlaylist(long specialId) {
        var infoJson = getJson(MOBILECDN + "/special/info?format=json&specialid=" + specialId);
        if (infoJson == null)
            return null;

        String name = null, author = null, cover = null;
        int songCount = 0;
        try {
            var data = infoJson.getAsJsonObject("data");
            name = str(data, "specialname");
            author = str(data, "nickname");
            cover = cover(str(data, "imgurl"));
            songCount = toInt(str(data, "songcount"));
        } catch (Exception e) {
            LinkDebug.log("KuGou playlist info EX", "id", specialId,
                    "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        var songs = new ArrayList<KuGouSongInfo>();
        int total = songCount;
        var loadedRaw = 0;   // number of items the server has cumulatively given, used only in the paging condition
        final int pageSize = 300;
        for (int page = 1; page <= 20; page++) {
            var songJson = getJson(MOBILECDN + "/special/song?format=json&specialid=" + specialId
                    + "&page=" + page + "&pagesize=" + pageSize + "&total_ver=2");
            if (songJson == null)
                break;
            int got = 0;
            try {
                var data = songJson.getAsJsonObject("data");
                total = toInt(str(data, "total"));
                for (var e : data.getAsJsonArray("info")) {
                    var o = e.getAsJsonObject();
                    var hash = normalizeHash(str(o, "hash"));
                    if (hash == null)
                        continue;
                    var fileName = str(o, "filename");
                    var title = fileName;
                    var artist = "";
                    if (fileName != null) {
                        var sep = fileName.indexOf(" - ");
                        if (sep > 0) {
                            artist = fileName.substring(0, sep).trim();
                            title = fileName.substring(sep + 3).trim();
                        }
                    }
                    // `got`/`loadedRaw` count "how many items the server gave", the paging condition needs it
                    got++;
                    loadedRaw++;
                    // [music cover] The track API carries the cover nested in `trans_param.union_cover` (shaped
                    // `http://imge.kugou.com/stdmusic/{size}/….jpg`), the only cover field a playlist gets.
                    var unionCover = nestedStr(o, "trans_param", "union_cover");
                    if (unionCover == null || unionCover.isEmpty())
                        unionCover = str(o, "imgurl");
                    if (unionCover == null || unionCover.isEmpty())
                        unionCover = str(o, "union_cover");
                    songs.add(new KuGouSongInfo(hash, title, artist,
                            secondsToMs(str(o, "duration")), null, cover(unionCover), null));
                }
            } catch (Exception e) {
                LinkDebug.log("KuGou playlist songs EX", "id", specialId, "page", page,
                        "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
                break;
            }
            // The paging condition must use "how many the server gave" (got / loadedRaw), not songs.size() — rows
            // dropped as invalid would end paging early:
            if (got < pageSize || (total > 0 && loadedRaw >= total))
                break;
        }

        LinkDebug.log("KuGou playlist", "id", specialId, "name", name, "author", author,
                "songCount", total, "loaded", songs.size(), "cover", cover);
        if (songs.isEmpty() && (name == null || name.isEmpty()))
            return null;
        return new KuGouPlaylist(specialId, name == null || name.isEmpty() ? "酷狗歌单 " + specialId : name,
                author == null ? "" : author, cover, total, songs);
    }

    /** Playlist metadata + tracks. */
    public record KuGouPlaylist(long id, String name, String author,
                                @Nullable String coverUrl, int songCount, List<KuGouSongInfo> songs) {
    }

    @Nullable
    private static JsonObject getJson(String url) {
        try {
            var request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", ANDROID_UA)
                    .header("Referer", "https://m.kugou.com/")
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            var result = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (result.statusCode() != 200) {
                LinkDebug.log("KuGou getJson", "url", url, "httpStatus", result.statusCode());
                return null;
            }
            return JsonParser.parseString(result.body()).getAsJsonObject();
        } catch (Exception e) {
            LinkDebug.log("KuGou getJson EX", "url", url,
                    "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /** Seconds (as a string) -> milliseconds; 0 when unparsable. */
    private static long secondsToMs(@Nullable String seconds) {
        if (seconds == null || seconds.isEmpty())
            return 0;
        try {
            return (long) (Double.parseDouble(seconds) * 1000);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Reads the **duration (milliseconds)** from the {@code getSongInfo} response, 0 when unavailable.
     */
    private static long durationMsFrom(JsonObject json) {
        // (1) top-level timeLength (seconds, the main source for free tracks)
        var top = secondsToMs(str(json, "timeLength"));
        if (top > 0)
            return top;

        // (2) millisecond fields inside extra (the true value for paid tracks lives here)
        var extra = json.getAsJsonObject("extra");
        if (extra != null) {
            for (var key : new String[]{"128timelength", "320timelength", "hightimelength"}) {
                var ms = millis(str(extra, key));
                if (ms > 0)
                    return ms;
            }
        }
        return 0;
    }

    /** Milliseconds (as a string) -> milliseconds; 0 when unparsable. (Split from secondsToMs so the units are not confused) */
    private static long millis(@Nullable String msText) {
        if (msText == null || msText.isEmpty())
            return 0;
        try {
            return (long) Double.parseDouble(msText);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** String -> int; 0 when unparsable. */
    private static int toInt(@Nullable String s) {
        if (s == null || s.isEmpty())
            return 0;
        try {
            return (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Probes "can this resolve audio now" with the **same API and headers** as playback, so the UI hint matches reality. */
    public static boolean isAudioUnavailable(String hash) {
        return fetchAudioUrl(hash) == null;
    }

    /**
     * Audio address usable right now: privilege-lite → signed API ({@code v5/url}) → public API; only a login token
     * makes the signed API hand out an address for paid/VIP tracks.
     */
    @Nullable
    public static String fetchAudioUrl(String hash) {
        return fetchAudioUrl(hash, null);
    }

    /** Same, but appends **why there is no address** (the platform's own words) to {@code detail}. */
    @Nullable
    public static String fetchAudioUrl(String hash, @Nullable StringBuilder detail) {
        var h = normalizeHash(hash);
        if (h == null)
            return null;

        // 0) daily VIP: the platform hands out a free 24h VIP once a day and the third-party PC clients claim it
        //    automatically; without it the signed API answers status=2 fail=["pkg","buy"] for VIP-only tracks while
        //    free ones play (see KuGouApi#ensureDailyVip). Once per process per day.
        var claimedNow = false;
        if (hasLogin()) {
            var claim = KuGouApi.ensureDailyVip();
            claimedNow = "received".equals(claim) || "upgraded".equals(claim);
            if (claimedNow)
                vipSummaryCache = null;   // the entitlement just changed, the cached note is stale
        }

        // 1) signed API: resolve at the "highest playable tier" first, drop to 128 on failure
        var quality = resolveQuality(h);
        var url = KuGouApi.songUrl(h, quality, detail);
        if (url == null && !"128".equals(quality)) {
            LinkDebug.log("KuGou quality fallback", "from", quality, "to", "128");
            if (detail != null)
                detail.setLength(0);   // keep only the last attempt's answer
            url = KuGouApi.songUrl(h, "128", detail);
        }
        // 1b) the VIP was granted during this very pass: give the platform a moment and ask once more — its
        //     entitlement cache is not always instant, and "the first load of the day" is exactly this case
        if (url == null && claimedNow && isEntitlementDenial(detail)) {
            try {
                Thread.sleep(800L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (detail != null)
                detail.setLength(0);
            url = KuGouApi.songUrl(h, quality, detail);
            if (url == null && !"128".equals(quality)) {
                if (detail != null)
                    detail.setLength(0);
                url = KuGouApi.songUrl(h, "128", detail);
            }
            LinkDebug.log("KuGou resolve retry after vip claim", "hash", h, "ok", url != null);
        }
        if (url != null)
            return url;

        // 2) public API fallback (no device registration needed; free tracks always make it here)
        var info = getSongInfo(h);
        if (info != null && info.audioUrl() != null)
            return info.audioUrl();
        return null;
    }

    /**
     * One short sentence for why the track cannot play: the signed API's refusal signature is the **same** for "not
     * signed in" and "must buy", so the login state has to be part of it.
     *
     * @param platformError the public metadata API's {@code error} field (e.g. "payment required")
     * @param resolveDetail what the signed URL call answered (see {@code KuGouApi#songUrl(String, String, StringBuilder)})
     * @param loggedIn      whether a login token is configured
     */
    public static String describeFailure(@Nullable String platformError, @Nullable String resolveDetail, boolean loggedIn) {
        return describeFailure(platformError, resolveDetail, loggedIn, null);
    }

    /**
     * Same, plus the account's VIP ({@link #vipSummary()}): these tracks answer {@code fail=["pkg","buy"]} whenever the
     * daily 24h VIP has lapsed, and play again once it is claimed.
     *
     * @param vipSummary a short account-VIP note, or null when unknown (see {@link #vipSummary()})
     */
    public static String describeFailure(@Nullable String platformError, @Nullable String resolveDetail, boolean loggedIn,
                                        @Nullable String vipSummary) {
        var detail = resolveDetail == null ? "" : resolveDetail;
        var err = platformError == null ? "" : platformError.strip();
        var quote = err.isEmpty() ? "" : dev.felnull.imp.client.music.MusicLoadFailure.text("imp.reason.kugou.quote", err);
        var vip = vipSummary == null || vipSummary.isEmpty() ? ""
                : dev.felnull.imp.client.music.MusicLoadFailure.text("imp.reason.kugou.vipSuffix", vipSummary);
        // `fail_process` lists the entitlement reasons, e.g. ["pkg","buy"] = must buy the album/single, ["vip"] = membership
        boolean mustBuy = detail.contains("buy") || detail.contains("pkg");
        boolean needsVip = detail.contains("vip");
        if (mustBuy)
            return loggedIn
                    ? dev.felnull.imp.client.music.MusicLoadFailure.text("imp.reason.kugou.mustBuy.loggedIn", vip + quote)
                    : dev.felnull.imp.client.music.MusicLoadFailure.text("imp.reason.kugou.mustBuy.anonymous", quote);
        if (needsVip)
            return loggedIn
                    ? dev.felnull.imp.client.music.MusicLoadFailure.text("imp.reason.kugou.vip.loggedIn", vip + quote)
                    : dev.felnull.imp.client.music.MusicLoadFailure.text("imp.reason.kugou.vip.anonymous", quote);
        if (!err.isEmpty())
            return loggedIn
                    ? dev.felnull.imp.client.music.MusicLoadFailure.text("imp.reason.kugou.restricted.loggedIn", vip, err)
                    : dev.felnull.imp.client.music.MusicLoadFailure.text("imp.reason.kugou.restricted.anonymous", err);
        return loggedIn
                ? dev.felnull.imp.client.music.MusicLoadFailure.text("imp.reason.kugou.noUrl.loggedIn", vip)
                : dev.felnull.imp.client.music.MusicLoadFailure.text("imp.reason.kugou.noUrl.anonymous");
    }

    /** Cached one-line account-VIP note (the query is an extra request, so it is reused for a while). */
    @Nullable
    private static volatile String vipSummaryCache;
    /** When {@link #vipSummaryCache} was filled in. */
    private static volatile long vipSummaryAtMs;
    /** How long the cached VIP note stays valid. */
    private static final long VIP_SUMMARY_TTL_MS = 30 * 60_000L;

    /** Short note such as "account VIP valid until … (svip)", or null when unreadable; cached for {@link #VIP_SUMMARY_TTL_MS}. */
    @Nullable
    public static String vipSummary() {
        var now = System.currentTimeMillis();
        var cached = vipSummaryCache;
        if (cached != null && now - vipSummaryAtMs < VIP_SUMMARY_TTL_MS)
            return cached;
        var state = KuGouApi.vipState();
        if (state == null)
            return cached;   // keep the stale note rather than dropping the information
        var until = state.until() == null ? "" : state.until();
        var type = state.vipType() == null || state.vipType().isEmpty() ? "" : "（" + state.vipType() + "）";
        var expired = state.expiredAt() == null ? "" : state.expiredAt();
        var note = state.active()
                ? "账号 VIP 有效至 " + until + type
                : "账号无有效 VIP" + (expired.isEmpty() ? "" : "（上次到期 " + expired + "）");
        vipSummaryCache = note;
        vipSummaryAtMs = now;
        return note;
    }

    /** Whether the platform's refusal was about **entitlement** (must buy / needs VIP) rather than "no such track". */
    private static boolean isEntitlementDenial(@Nullable StringBuilder detail) {
        if (detail == null)
            return false;
        var d = detail.toString();
        return d.contains("buy") || d.contains("pkg") || d.contains("vip");
    }

    /** Intersects the quality wanted in config with "the highest playable tier of this music" (drop when the playable tier is below the wanted one; try the configured one when the privilege is unknown). */
    private static String resolveQuality(String hash) {
        var wanted = configuredQuality();
        var available = KuGouApi.privilegeLite(hash);
        if (available == null || available.isEmpty())
            return wanted;
        var wantedRank = qualityRank(wanted);
        var bestRank = -1;
        for (var q : available)
            bestRank = Math.max(bestRank, qualityRank(q));
        // wanted tier playable -> use it; otherwise drop to the highest playable tier; if none, 128
        if (bestRank < 0)
            return "128";
        if (wantedRank >= 0 && wantedRank <= bestRank)
            return wanted;
        return QUALITY_ORDER[bestRank];
    }

    /** Quality tiers (low -> high): 128 < 320 < flac < high (Hi-Res). */
    private static final String[] QUALITY_ORDER = {"128", "320", "flac", "high"};

    private static int qualityRank(String q) {
        for (int i = 0; i < QUALITY_ORDER.length; i++)
            if (QUALITY_ORDER[i].equals(q))
                return i;
        return -1;
    }

    /** Whether login state is configured (either token/userid non-empty) -- used to tell the "login required" and "login would not help" hints apart. */
    public static boolean hasLogin() {
        try {
            var cfg = IamMusicPlayer.getConfig().kugouConfig;
            return (cfg.token != null && !cfg.token.isEmpty())
                    || (cfg.userid != null && !cfg.userid.isEmpty())
                    || (cfg.cookie != null && cfg.cookie.contains("token="));
        } catch (Exception e) {
            return false;
        }
    }

    /** The quality from config (empty/invalid always counts as 128). */
    private static String configuredQuality() {
        try {
            var q = IamMusicPlayer.getConfig().kugouConfig.quality;
            if (q == null || q.isEmpty())
                return "128";
            q = q.trim().toLowerCase(java.util.Locale.ROOT);
            return switch (q) {
                case "128", "320", "flac", "high", "viper_atmos", "viper_clear", "viper_tape" -> q;
                default -> "128";
            };
        } catch (Exception e) {
            return "128";
        }
    }

    /**
     * Fetches the metadata of one music and assembles it into IMP's media result (title/artist/duration/cover).
     */
    @Nullable
    public static MusicMediaResult getMusicMediaResult(String hash) {
        var info = getSongInfo(hash);
        if (info == null || info.name() == null || info.name().isEmpty())
            return null;

        // Duration unavailable (neither top-level timeLength nor extra): **do not create a source** — duration 0
        // makes the player claim "finished" on the first tick, so the slot dies with an address that never plays.
        if (info.durationMs() <= 0) {
            LinkDebug.log("KuGou getMusicMediaResult SKIP", "hash", hash, "name", info.name(),
                    "reason", "no duration (timeLength & extra.*timelength both empty)");
            return null;
        }

        var source = new MusicSource("kugou", info.hash(), info.durationMs());
        // The public API has no cover for some tracks, so fall back to privilege-lite's info.image
        // (EMPTY only when both routes fail — that does not block source creation).
        var coverUrl = info.coverUrl();
        if (coverUrl == null || coverUrl.isEmpty()) {
            var fallback = KuGouApi.privilegeLiteCover(info.hash());
            if (fallback != null && !fallback.isEmpty())
                coverUrl = cover(fallback);
        }
        var image = coverUrl == null || coverUrl.isEmpty()
                ? ImageInfo.EMPTY
                : new ImageInfo(ImageInfo.ImageType.URL, coverUrl);
        return new MusicMediaResult(source, image, info.name(), info.author() == null ? "" : info.author());
    }

    // ------------------------------------------------------------------ lyrics

    /** Lyrics search API. */
    private static final String LYRIC_SEARCH = "http://lyrics.kugou.com/search";
    /** Lyrics body API (returns the lyrics text base64 encoded). */
    private static final String LYRIC_DOWNLOAD = "https://lyrics.kugou.com/download";

    /** Failure / no lyrics always returns {@link Lyric#EMPTY} (the caller quietly shows nothing). */
    @Nullable
    public static Lyric getLyric(String hash) {
        var h = normalizeHash(hash);
        if (h == null)
            return null;

        try {
            var search = getJson(LYRIC_SEARCH + "?hash=" + h + "&keyword=&lrctxt=1&man=yes");
            if (search == null)
                return Lyric.EMPTY;
            var candidates = search.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                LinkDebug.log("KuGou lyric no candidates", "hash", h,
                        "status", str(search, "status"));
                return Lyric.EMPTY;
            }
            var first = candidates.get(0).getAsJsonObject();
            var id = str(first, "id");
            var accessKey = str(first, "accesskey");
            if (id == null || id.isEmpty() || accessKey == null || accessKey.isEmpty())
                return Lyric.EMPTY;

            var cltime = System.currentTimeMillis() / 1000L;
            var url = LYRIC_DOWNLOAD + "?ver=1&client=android&id=" + enc(id) + "&accesskey=" + enc(accessKey)
                    + (kugouKrc() ? "&fmt=krc" : "&fmt=lrc")
                    + "&charset=utf8&dfid=-&mid=-&uuid=-&appid=" + KuGouSignature.APPID
                    + "&clientver=" + KuGouSignature.CLIENTVER + "&clienttime=" + cltime;

            var get = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", ANDROID_UA)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            var resp = HTTP_CLIENT.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                LinkDebug.log("KuGou lyric download", "hash", h, "httpStatus", resp.statusCode());
                return Lyric.EMPTY;
            }
            var root = JsonParser.parseString(resp.body()).getAsJsonObject();
            var b64 = str(root, "content");
            if (b64 == null || b64.isEmpty()) {
                LinkDebug.log("KuGou lyric download", "hash", h, "status", str(root, "status"),
                        "reason", "content empty");
                return Lyric.EMPTY;
            }

            var raw = Base64.getDecoder().decode(b64);
            if (raw.length >= 4 && raw[0] == 'k' && raw[1] == 'r' && raw[2] == 'c') {
                // [krc main path] The translation exists **only in krc**: `fmt=lrc` has no translation lines, krc
                // carries a base64 `[language:…]` line with a per-line translation array aligned to the original lines.
                var krc = parseKrc(raw, kugouLyricTranslation());
                if (krc != null) {
                    LinkDebug.log("KuGou lyric", "hash", h, "fmt", str(root, "fmt"),
                            "bytes", raw.length, "hasTrans", krc.hasTrans());
                    return krc;
                }
                LinkDebug.log("KuGou krc decode failed", "hash", h, "bytes", raw.length);
                return Lyric.EMPTY;
            }
            // The server sometimes answers plain lrc instead: parse it as before, so a krc problem never means no lyrics
            var lrc = new String(raw, StandardCharsets.UTF_8);
            var lyric = parseLrc(lrc);
            LinkDebug.log("KuGou lyric", "hash", h, "fmt", str(root, "fmt"),
                    "bytes", raw.length, "hasTrans", lyric.hasTrans());
            return lyric;
        } catch (Exception e) {
            LinkDebug.log("KuGou lyric EX", "hash", h,
                    "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
            return Lyric.EMPTY;
        }
    }

    /** The "use krc for lyrics" switch in config (on by default); on a config read error treat it as on, so lyrics never disappear because of a config read. */
    private static boolean kugouKrc() {
        try {
            return IamMusicPlayer.getConfig().kugouConfig.lyricUseKrc;
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean kugouLyricTranslation() {
        try {
            return IamMusicPlayer.getConfig().kugouConfig.lyricTranslation;
        } catch (Throwable t) {
            return true;
        }
    }

    /** krc XOR key (publicly fixed: 16-byte repeating XOR). */
    private static final byte[] KRC_KEY = {
            0x40, 0x47, 0x61, 0x77, 0x5E, 0x32, 0x74, 0x47,
            0x51, 0x36, 0x31, 0x2D, (byte) 0xCE, (byte) 0xD2, 0x6E, 0x69};

    /** krc body line: {@code [startMs,durationMs]<offset,duration,0>words...}. */
    private static final Pattern KRC_LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");

    /** krc per-character time tags {@code <..>}, all stripped when taking a whole line. */
    private static final Pattern KRC_TAG = Pattern.compile("<[^>]*>");

    /** One krc per-character entry: {@code <offset,duration,0>word text...} (text runs to the next {@code <}). */
    private static final Pattern KRC_WORD = Pattern.compile("<(\\d+),(\\d+),\\d+>([^<]*)");

    /**
     * Decodes one krc ({@code krc1} header + repeating XOR + zlib), returns the original text and the **translation**.
     */
    @Nullable
    private static Lyric parseKrc(byte[] raw, boolean withTranslation) {
        try {
            var body = new byte[raw.length - 4];
            for (int i = 4; i < raw.length; i++)
                body[i - 4] = (byte) (raw[i] ^ KRC_KEY[(i - 4) % KRC_KEY.length]);

            var text = inflateKrc(body);
            if (text == null)
                return null;
            return parseKrcText(text, withTranslation);
        } catch (Exception e) {
            LinkDebug.log("KuGou krc parse EX",
                    "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /** zlib decompression (Inflater); null on failure. */
    @Nullable
    private static String inflateKrc(byte[] data) {
        var inflater = new java.util.zip.Inflater();
        try {
            inflater.setInput(data);
            var out = new java.io.ByteArrayOutputStream();
            var buf = new byte[8192];
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n == 0)
                    break;
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        } finally {
            inflater.end();
        }
    }

    /** Splits krc text -> {@link Lyric} (original + translations paired by line number + per-character). */
    private static Lyric parseKrcText(String text, boolean withTranslation) {
        var original = new Float2ObjectLinkedOpenHashMap<String>();
        var trans = new Float2ObjectLinkedOpenHashMap<String>();
        var words = new Float2ObjectLinkedOpenHashMap<List<Lyric.Word>>();
        var times = new ArrayList<Float>();
        var texts = new ArrayList<String>();
        var transLines = new ArrayList<String>();
        var offsetSec = 0f;

        for (var rawLine : text.split("\n")) {
            var line = rawLine.strip();
            if (line.isEmpty())
                continue;

            if (line.startsWith("[offset:")) {
                try {
                    offsetSec = Float.parseFloat(
                            line.substring("[offset:".length(), line.indexOf(']')).strip()) / 1000f;
                } catch (Exception ignored) {
                }
                continue;
            }

            var lang = krcLanguageLines(line);
            if (lang != null) {
                if (transLines.isEmpty())
                    transLines.addAll(lang);
                continue;
            }

            var m = KRC_LINE.matcher(line);
            if (!m.matches())
                continue;   // metadata lines ([id:]/[ar:]/[ti:]/[al:]/...) are skipped

            float t;
            try {
                t = Integer.parseInt(m.group(1)) / 1000f + offsetSec;
            } catch (NumberFormatException ignored) {
                continue;
            }
            times.add(t);
            texts.add(KRC_TAG.matcher(m.group(3)).replaceAll("").strip());
            var lineWords = parseKrcWords(m.group(3), t);
            if (lineWords != null)
                words.put(t, lineWords);
        }

        for (int i = 0; i < times.size(); i++) {
            var t = times.get(i);
            var orig = texts.get(i);
            original.put(t, orig);

            if (i < transLines.size()) {
                var tr = transLines.get(i) == null ? "" : transLines.get(i).strip();
                // Skip the translation when it equals the original or is empty (for some music the krc translation is identical to the original)
                if (!tr.isEmpty() && !tr.equals(orig))
                    trans.put(t, tr);
            }
        }

        if (original.isEmpty())
            return Lyric.EMPTY;
        return new Lyric(original, (!withTranslation || trans.isEmpty()) ? null : trans,
                words.isEmpty() ? null : words);
    }

    /** Splits one line's per-character entries ({@code <offset,duration,0>word...}) -> word list; null when there are no per-character tags. */
    @Nullable
    private static List<Lyric.Word> parseKrcWords(String content, float lineStart) {
        var matcher = KRC_WORD.matcher(content);
        var out = new ArrayList<Lyric.Word>();
        while (matcher.find()) {
            try {
                var offset = Integer.parseInt(matcher.group(1)) / 1000f;
                var dur = Integer.parseInt(matcher.group(2)) / 1000f;
                var text = matcher.group(3);
                if (text.isEmpty())
                    continue;
                out.add(new Lyric.Word(text, lineStart + offset, dur));
            } catch (NumberFormatException ignored) {
            }
        }
        return out.isEmpty() ? null : out;
    }

    /** Parses {@code [language:<base64>]} -> per-line translations; null when this is not that line / cannot be decoded. */
    @Nullable
    private static List<String> krcLanguageLines(String line) {
        if (!line.startsWith("[language:"))
            return null;
        try {
            var b64 = line.substring("[language:".length(), line.lastIndexOf(']'));
            var json = JsonParser.parseString(
                    new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8)).getAsJsonObject();
            var content = json.getAsJsonArray("content");
            if (content == null)
                return null;

            // Each `content[]` segment carries `type`: 1=translation, 0=romaji. The UI only has "original + translation",
            // so **only the type==1 segment may be taken** (a romaji shown as a translation would be a mistake);
            // a segment without `type` is the fallback.
            List<String> fallback = null;
            for (var el : content) {
                if (!el.isJsonObject())
                    continue;
                var obj = el.getAsJsonObject();
                var lines = flattenLyricContent(obj.getAsJsonArray("lyricContent"));
                if (lines == null || lines.isEmpty())
                    continue;
                if (obj.has("type") && obj.get("type").getAsInt() == 1)
                    return lines;
                if (fallback == null && !obj.has("type"))
                    fallback = lines;
            }
            return fallback;
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Flattens {@code lyricContent} (an array of string arrays, per line, each line possibly an array of words) into per-line strings. */
    @Nullable
    private static List<String> flattenLyricContent(@Nullable com.google.gson.JsonArray lc) {
        if (lc == null)
            return null;
        var out = new ArrayList<String>();
        for (var l : lc) {
            if (l.isJsonArray()) {
                var sb = new StringBuilder();
                for (var w : l.getAsJsonArray())
                    sb.append(w.getAsString());
                out.add(sb.toString());
            } else {
                out.add(l.getAsString());
            }
        }
        return out;
    }

    /** LRC timestamp line: {@code [mm:ss.xx]} / {@code [mm:ss.xxx]} (same convention as the Netease channel). */
    private static final Pattern LRC_TIME = Pattern.compile("^\\[(\\d+):(\\d+)[.:](\\d+)](.*)$");

    /**
     * Parses LRC text -> {@link Lyric}.
     */
    private static Lyric parseLrc(String lrc) {
        var original = new Float2ObjectLinkedOpenHashMap<String>();
        var offsetSec = 0f;

        // [instrumentation, logging only] A translation in KuGou's lrc shows up as a second line with the same
        // timestamp, but this method builds only the original map (hasTrans() is always false here) — so the count
        // says whether the API's lrc ever carries translations at all (they normally have to come from krc).
        var seenText = new java.util.HashMap<Float, String>();
        int textLines = 0;
        int timeSlots = 0;
        int dupTimestamps = 0;
        String dupSample = "";

        for (var rawLine : lrc.split("\n")) {
            var line = rawLine.strip();
            if (line.isEmpty())
                continue;

            if (line.startsWith("[offset:")) {
                try {
                    var v = line.substring("[offset:".length(), line.indexOf(']')).strip();
                    offsetSec = Float.parseFloat(v) / 1000f;
                } catch (Exception ignored) {
                }
                continue;
            }

            // One line can carry several [mm:ss.xx] (one lyric line with multiple time points): take each of them
            var rest = line;
            var times = new ArrayList<Float>();
            while (true) {
                var m = LRC_TIME.matcher(rest);
                if (!m.find())
                    break;
                try {
                    var minutes = Integer.parseInt(m.group(1));
                    var seconds = Integer.parseInt(m.group(2));
                    var frac = Float.parseFloat("0." + m.group(3));
                    times.add(minutes * 60 + seconds + frac + offsetSec);
                } catch (NumberFormatException ignored) {
                }
                rest = m.group(4);
            }
            if (times.isEmpty())
                continue;

            var text = rest.strip();
            if (text.isEmpty())
                continue;   // a pure timestamp (empty line) is not displayed

            textLines++;
            for (var t : times) {
                timeSlots++;
                var prev = seenText.putIfAbsent(t, text);
                if (prev != null) {
                    dupTimestamps++;
                    if (dupSample.isEmpty())
                        dupSample = "[" + t + "] 《" + prev + "》 vs 《" + text + "》";
                }
                original.put(t, text);
            }
        }

        LinkDebug.log("KuGou lrc stats", "textLines", textLines, "timeSlots", timeSlots,
                "uniqueTimestamps", original.size(), "dupTimestamps", dupTimestamps,
                "sample", dupSample.isEmpty() ? "(没有同时间戳的第二行)" : dupSample);

        if (original.isEmpty())
            return Lyric.EMPTY;
        return new Lyric(original);
    }


    private static KuGouSongInfo parse(String hash, JsonObject json) {
        var author = str(json, "singerName");
        // Title: split fileName ("artist - title") at the first " - " and use the tail as the name (same convention as search/playlist);
        // fall back to songName when it cannot be split.
        var name = titleFromFileName(str(json, "fileName"));
        if (name == null || name.isEmpty())
            name = str(json, "songName");
        // Duration: top-level timeLength (seconds) takes priority, falling back to extra.*timelength (milliseconds) -- see durationMsFrom
        long durationMs = durationMsFrom(json);
        var url = str(json, "url");
        // Cover: imgUrl is empty for some tracks while album_img has an image -- album_img is the fallback (privilege-lite comes later).
        var coverUrl = str(json, "imgUrl");
        if (coverUrl == null || coverUrl.isEmpty())
            coverUrl = str(json, "album_img");
        return new KuGouSongInfo(hash, name, author, durationMs,
                url == null || url.isEmpty() ? null : url,
                cover(coverUrl),
                str(json, "error"));
    }

    /** Takes the title from fileName ("artist - title"); null when there is no " - " or the result is empty. */
    @Nullable
    private static String titleFromFileName(@Nullable String fileName) {
        if (fileName == null || fileName.isEmpty())
            return null;
        var sep = fileName.indexOf(" - ");
        if (sep <= 0)
            return null;
        var title = fileName.substring(sep + 3).trim();
        return title.isEmpty() ? null : title;
    }

    private static String str(JsonObject o, String key) {
        var e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    /**
     * Reads a string field from a **nested object**: {@code o.<inner>.<key>}.
     */
    @Nullable
    private static String nestedStr(JsonObject o, String inner, String key) {
        if (o == null || !o.has(inner))
            return null;
        var e = o.get(inner);
        if (e == null || !e.isJsonObject())
            return null;
        return str(e.getAsJsonObject(), key);
    }

    /** Cover: fill in the size placeholder, normalize http to https (some networks/proxies block plain http). */
    @Nullable
    private static String cover(@Nullable String imgUrl) {
        if (imgUrl == null || imgUrl.isEmpty())
            return null;
        return imgUrl.replace("{size}", COVER_SIZE).replaceFirst("^http://", "https://");
    }

    /** Encoding for URL concatenation (current parameters are all hash/numbers, kept for future extension). */
    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /**
     * The data fetch result of one KuGou music.
     *
     * @param hash       normalized hash (lowercase)
     * @param name       title
     * @param author     artist
     * @param durationMs duration (milliseconds; 0 when unavailable)
     * @param audioUrl   the audio address **as of now** (expires; null = unavailable, paid/login required)
     * @param coverUrl   cover URL (size filled in, https)
     * @param error      the reason given by the API (e.g. "payment required")
     */
    public record KuGouSongInfo(String hash, String name, String author, long durationMs,
                                @Nullable String audioUrl, @Nullable String coverUrl, @Nullable String error) {
    }
}
