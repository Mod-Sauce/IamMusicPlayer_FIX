package dev.felnull.imp.client.bilibili;

import com.google.gson.*;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.MusicLoadFailure;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.client.music.media.MusicMediaResult;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Lyric;
import dev.felnull.imp.music.resource.MusicSource;
import dev.felnull.imp.util.ProxyUtil;
import it.unimi.dsi.fastutil.floats.Float2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.floats.Float2ObjectSortedMap;
import oshi.util.tuples.Pair;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BiliBiliUtil {
    private static final Gson GSON = new Gson();
    // api.bilibili.com answers an over-short UA with HTTP 412 + an HTML risk page: always send the full Chrome UA.
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    public static final String REFERER = "https://www.bilibili.com/";
    public static final String ORIGIN = "https://www.bilibili.com";
    /**
     * Built-in {@code cdnOrder}: the CDN families that answer at once, then the P2P edge that often does not.
     */
    private static final String DEFAULT_CDN_ORDER = "upos-,bilivideo,mcdn-";

    /**
     * Headers a resolved CDN address must carry — shared with {@code BilibiliHttpAudioSourceManager} so the probe
     * predicts the real fetch: header-less requests get HTTP 403 + an HTML page (read as not audio).
     */
    public static Map<String, String> cdnHeaders(String url) {
        return Map.of("User-Agent", USER_AGENT, "Referer", REFERER, "Origin", ORIGIN);
    }

    /** Connect and single-request timeouts: these requests are sent from the **loading thread**, and without a timeout a hung connection would hang the load forever. */
    private static final java.time.Duration CONNECT_TIMEOUT = java.time.Duration.ofSeconds(10);
    private static final java.time.Duration REQUEST_TIMEOUT = java.time.Duration.ofSeconds(15);
    /** Anonymous browser fingerprint: {@code b_3}/{@code b_4} of this endpoint are the buvid3/buvid4 cookies. */
    private static final String SPI_URL = "https://api.bilibili.com/x/frontend/finger/spi";
    private static final long BUVID_TTL_MS = 12L * 60 * 60 * 1000;
    private static volatile String buvidPair;
    private static volatile long buvidAt;
    private static volatile HttpClient clientInstance;

    /**
     * Shared HTTP client, built lazily: {@code ProxyUtil.getProxy()} reads the mod config, so a static initialiser
     * would break standalone use. Package-private so the WBI / space / login helpers reuse proxy and timeouts.
     */
    static HttpClient client() {
        var c = clientInstance;
        if (c == null) {
            synchronized (BiliBiliUtil.class) {
                c = clientInstance;
                if (c == null) {
                    var builder = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT);
                    try {
                        var proxy = ProxyUtil.getProxy();
                        var address = proxy == null ? null : proxy.address();
                        if (address instanceof InetSocketAddress inet)
                            builder.proxy(ProxySelector.of(inet));
                    } catch (Throwable t) {
                        // No (or not yet loaded) config: go direct rather than failing the whole utility
                    }
                    c = builder.build();
                    clientInstance = c;
                }
            }
        }
        return c;
    }

    /** Unified Bilibili API headers: full Chrome UA + Referer + Origin, plus the Cookie if there is one. */
    static HttpRequest.Builder apiRequest(String url, String cookie) {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .header("Origin", ORIGIN);

        var ck = (cookie == null || cookie.isEmpty()) ? configCookie() : cookie;
        if (!ck.isEmpty())
            builder.header("Cookie", ck);

        return builder;
    }

    /**
     * The configured cookie ({@code bilibiliConfig.bilibiliCookie}), overridable for debug with
     * {@code -Diammusicplayer.bilibili.cookie=…} / {@code IMP_BILIBILI_COOKIE} since the config only reads in-game.
     */
    private static String configCookie() {
        var override = cookieOverride();
        if (!override.isEmpty())
            return normalizeCookie(override, "override");
        try {
            var c = IamMusicPlayer.getConfig().bilibiliConfig.bilibiliCookie;
            return normalizeCookie(c == null ? "" : c.trim(), "config");
        } catch (Throwable t) {
            return "";
        }
    }

    /** Accepts a plain {@code name=value; …} header or a phone-client JSON export; only the names are logged. */
    private static String normalizeCookie(String raw, String source) {
        if (raw == null || raw.isBlank())
            return "";
        var parsed = BiliBiliCookie.parse(raw);
        if (parsed.isEmpty())
            return "";
        if (!parsed.equals(raw))
            LinkDebug.log("BiliBili cookie parsed", "source", source, "names", BiliBiliCookie.namesOf(parsed));
        return parsed;
    }

    /** A null {@code cookie} still reaches Bilibili as the configured one ({@link #apiRequest}), so the log must not print the raw parameter. */
    private static boolean cookieSent(String cookie) {
        return (cookie != null && !cookie.isEmpty()) || !configCookie().isEmpty();
    }

    private static String cookieOverride() {
        try {
            var prop = System.getProperty("iammusicplayer.bilibili.cookie");
            if (prop != null && !prop.isBlank())
                return prop.trim();
            var env = System.getenv("IMP_BILIBILI_COOKIE");
            return env == null ? "" : env.trim();
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * Anonymous cookie trio B站 risk control wants, from {@code x/frontend/finger/spi} plus a fresh {@code b_nut}.
     * buvid3 alone ⇒ {@code -352}, with buvid4 ⇒ HTTP 412, plus b_nut ⇒ code=0; a burst still hits 412, so callers pace.
     */
    public static String anonCookie() {
        try {
            var pair = buvidPair;
            if (pair == null || System.currentTimeMillis() - buvidAt > BUVID_TTL_MS) {
                var response = client().send(apiRequest(SPI_URL, "").GET().build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200)
                    return "";
                var json = JsonParser.parseString(response.body()).getAsJsonObject();
                var data = json.getAsJsonObject("data");
                if (data == null || !data.has("b_3") || !data.has("b_4"))
                    return "";
                pair = "buvid3=" + data.get("b_3").getAsString() + "; buvid4=" + data.get("b_4").getAsString();
                buvidPair = pair;
                buvidAt = System.currentTimeMillis();
            }
            return pair + "; b_nut=" + (System.currentTimeMillis() / 1000);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * The cookie a space API call should carry: the anonymous trio plus every configured (logged-in) entry it does
     * not already contain, so a QR login keeps working while the fingerprint headers stay present.
     */
    public static String spaceCookie() {
        var anon = anonCookie();
        var cfg = configCookie();
        if (cfg.isEmpty())
            return anon;
        var sb = new StringBuilder(anon);
        for (var part : cfg.split(";")) {
            var kv = part.strip();
            if (kv.isEmpty() || !kv.contains("="))
                continue;
            var name = kv.substring(0, kv.indexOf('=')).strip();
            if (!anon.contains(name + "=")) {
                if (sb.length() > 0)
                    sb.append("; ");
                sb.append(kv);
            }
        }
        return sb.toString();
    }

    /** The part number of an identifier ({@code 1} with no suffix); the convention lives in {@code BiliBiliIds}. */
    public static int pageOf(String identifier) {
        return dev.felnull.imp.util.BiliBiliIds.pageOf(identifier);
    }

    /** Strip the part suffix to get the bare video number (both video info lookup and source creation use the bare number). */
    public static String stripPage(String identifier) {
        return dev.felnull.imp.util.BiliBiliIds.stripPage(identifier);
    }

    /**
     * The watch address of a video, for 「复制链接」; {@code page > 1} adds the {@code ?p=N} the site uses for a part.
     */
    public static String videoUrl(String bvid, int page) {
        var bare = stripPage(bvid);
        var url = "https://www.bilibili.com/video/" + (bare == null ? "" : bare);
        return page > 1 ? url + "?p=" + page : url;
    }

    public static String fetchAudioUrl(String bvid) throws IOException, InterruptedException {
        return fetchAudioUrl(bvid, 1, null);
    }

    public static String fetchAudioUrl(String bvid, int page, String cookie) throws IOException, InterruptedException {
        return fetchAudioCandidates(bvid, page, cookie).get(0);
    }

    /** Like {@link #fetchAudioUrl}, but returns every address best first, so callers fall back to another CDN host. */
    public static List<String> fetchAudioCandidates(String bvid, int page, String cookie) throws IOException, InterruptedException {
        String viewApi = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
        HttpRequest.Builder viewRequestBuilder = apiRequest(viewApi, cookie);

        HttpResponse<String> viewResponse = client().send(viewRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        // Check the status first: the 412 HTML risk-control page would throw an unchecked JsonSyntaxException, while callers catch only IOException.
        if (viewResponse.statusCode() != 200) {
            dev.felnull.imp.client.music.link.LinkDebug.log("BiliBili view HTTP", "bv", bvid,
                    "httpStatus", viewResponse.statusCode(), "cookieSent", cookieSent(cookie),
                    "bodyHead", viewResponse.body().substring(0, Math.min(160, viewResponse.body().length())));
            throw new IOException("Bilibili view HTTP " + viewResponse.statusCode());
        }

        JsonObject viewJson = JsonParser.parseString(viewResponse.body()).getAsJsonObject();

        // [logging only] Records the HTTP status + Bilibili code/message, so a failure is not just "No available loaders found" in the log.
        if (dev.felnull.imp.client.music.link.LinkDebug.isEnabled()) {
            dev.felnull.imp.client.music.link.LinkDebug.log("BiliBili view", "bv", bvid,
                    "httpStatus", viewResponse.statusCode(),
                    "code", viewJson.has("code") ? viewJson.get("code").getAsString() : "?",
                    "message", viewJson.has("message") ? viewJson.get("message").getAsString() : "?",
                    "cookieSent", cookieSent(cookie),
                    "bodyHead", viewResponse.body().substring(0, Math.min(160, viewResponse.body().length())));
        }

        if (viewJson.get("code").getAsInt() != 0) {
            throw new IOException("Failed to get video info: " + viewJson.get("message").getAsString());
        }

        JsonObject viewData = viewJson.getAsJsonObject("data");

        // Playing a video already fetched this response: hand its video-level facts to the local cache too, so the 分P
        // page can show the cover/uploader/counts/publish date **without a request of its own**. Never fatal.
        try {
            if (viewData != null)
                BiliBiliArcTags.getInstance().rememberInfo(bvid, BiliBiliParts.infoOf(viewData));
        } catch (Throwable ignored) {
        }

        long cid;

        JsonArray pagesArray = viewData.getAsJsonArray("pages");
        if (pagesArray != null && pagesArray.size() > 1) {
            if (page > 0 && page <= pagesArray.size()) {
                JsonObject pageData = pagesArray.get(page - 1).getAsJsonObject();
                cid = pageData.get("cid").getAsLong();
            } else {
                JsonObject pageData = pagesArray.get(0).getAsJsonObject();
                cid = pageData.get("cid").getAsLong();
            }
        } else {
            cid = viewData.get("cid").getAsLong();
        }

        // 顺手记下"这首歌是哪个分P 的 cid"：SponsorBlock 的片段按 cid 存，播放时要靠它归类，这样不必再多打一次请求。
        BiliBiliSponsorSegments.rememberCid(bvid,
                pagesArray != null && pagesArray.size() > 1 && page > 0 && page <= pagesArray.size() ? page : 1, cid);

        String playApi = "https://api.bilibili.com/x/player/playurl?bvid=" + bvid +
                "&cid=" + cid + "&fnval=4048";

        HttpRequest.Builder playRequestBuilder = apiRequest(playApi, cookie);

        HttpResponse<String> playResponse = client().send(playRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (playResponse.statusCode() != 200) {
            dev.felnull.imp.client.music.link.LinkDebug.log("BiliBili playurl HTTP", "bv", bvid, "cid", cid,
                    "httpStatus", playResponse.statusCode(), "cookieSent", cookieSent(cookie),
                    "bodyHead", playResponse.body().substring(0, Math.min(160, playResponse.body().length())));
            throw new IOException("Bilibili playurl HTTP " + playResponse.statusCode());
        }

        JsonObject playJson = JsonParser.parseString(playResponse.body()).getAsJsonObject();

        // [logging only] Records the real response of this address fetch, including whether it carried a Cookie.
        if (dev.felnull.imp.client.music.link.LinkDebug.isEnabled()) {
            var code = playJson.has("code") ? playJson.get("code").getAsString() : "?";
            var msg = playJson.has("message") ? playJson.get("message").getAsString() : "?";
            var hasDash = playJson.has("data") && playJson.getAsJsonObject("data").has("dash");
            var audioCount = hasDash
                    ? playJson.getAsJsonObject("data").getAsJsonObject("dash").getAsJsonArray("audio").size() : -1;
            dev.felnull.imp.client.music.link.LinkDebug.log("BiliBili playurl", "bv", bvid, "cid", cid,
                    "httpStatus", playResponse.statusCode(), "code", code, "message", msg,
                    "hasDash", hasDash, "audioCount", audioCount,
                    "cookieSent", cookieSent(cookie),
                    "bodyHead", playResponse.body().substring(0, Math.min(160, playResponse.body().length())));
        }

        if (playJson.get("code").getAsInt() != 0) {
            throw new IOException("Bilibili API error: " + playJson.get("message").getAsString());
        }

        JsonObject data = playJson.getAsJsonObject("data");
        if (!data.has("dash")) {
            throw new IOException("DASH stream not available");
        }

        JsonObject dash = data.getAsJsonObject("dash");
        JsonArray audioArray = dash.getAsJsonArray("audio");
        if (audioArray == null || audioArray.isEmpty()) {
            throw new IOException("No audio stream found");
        }

        var candidates = audioCandidates(dash);
        if (candidates.isEmpty()) {
            throw new IOException("No audio stream found");
        }
        return candidates;
    }

    /**
     * The audio addresses, best first — every {@code backupUrl} of the highest-bandwidth stream, then the next stream
     * down. Order comes from {@code bilibiliConfig.cdnOrder} ({@link #hostRank}): B站 puts a P2P edge
     * ({@code mcdn.*:8082}) in {@code baseUrl} that often times out on the first connect, so a track goes silent.
     */
    static List<String> audioCandidates(JsonObject dash) {
        JsonArray audioArray = dash.getAsJsonArray("audio");
        var streams = new ArrayList<JsonObject>();
        for (var elem : audioArray) {
            if (elem.isJsonObject()) streams.add(elem.getAsJsonObject());
        }
        // 解码只认 AAC 与 FLAC（Dolby/ec-3 等不要）。**Hi-Res(FLAC) 排在 AAC 前面**：AAC 只是保底，
        // LavaPlayer 2.2.4 自带 FLAC 容器（KuGou 的 flac 音质一直在用），所以没理由把它过滤掉。
        var flac = new ArrayList<JsonObject>();
        var aac = new ArrayList<JsonObject>();
        for (var s : streams) {
            var codecs = codecsOf(s);
            if (codecs.startsWith("fLaC"))
                flac.add(s);
            else if (codecs.contains("mp4a"))
                aac.add(s);
        }
        flac.sort((a, b) -> Integer.compare(bandwidthOf(b), bandwidthOf(a)));
        aac.sort((a, b) -> Integer.compare(bandwidthOf(b), bandwidthOf(a)));
        var ordered = new ArrayList<JsonObject>();
        ordered.addAll(flac);
        ordered.addAll(aac);
        // 两种都不认识时才退成"全都试"（与以前一致）
        streams = ordered.isEmpty() ? streams : ordered;
        if (ordered.isEmpty())
            streams.sort((a, b) -> Integer.compare(bandwidthOf(b), bandwidthOf(a)));
        // B站 的 Hi-Res 不在 audio 数组里，而在 dash.flac：display 表示视频有这个档、audio 表示这次给了地址；
        // 两个一起看才能分出「B站没给」与「给了但 LavaPlayer 没解出来」。
        var flacBlock = dash.has("flac") && dash.get("flac").isJsonObject() ? dash.getAsJsonObject("flac") : null;
        LinkDebug.log("BiliBili audio streams", "offered", audioArray.size(), "flac", flac.size(),
                "aac", aac.size(), "first", codecsOf(streams.get(0)),
                "firstBandwidth", bandwidthOf(streams.get(0)),
                "codecs", describeStreams(streams),
                "flacDisplay", boolOf(flacBlock, "display"),
                "flacAudio", flacBlock != null && flacBlock.has("audio") && !flacBlock.get("audio").isJsonNull());

        var out = new ArrayList<String>();
        for (var s : streams) {
            // 去重按**每个流**算：不同码率的文件即使同 CDN 也是不同文件，跨流去重会把 AAC 保底候选丢掉
            var seenHosts = new ArrayList<String>();
            var urls = new ArrayList<String>();
            if (s.has("baseUrl") && !s.get("baseUrl").isJsonNull())
                urls.add(s.get("baseUrl").getAsString());
            if (s.has("backupUrl") && s.get("backupUrl").isJsonArray()) {
                for (var b : s.getAsJsonArray("backupUrl")) {
                    if (b.isJsonPrimitive()) urls.add(b.getAsString());
                }
            }
            // One address per host per stream: the same CDN twice is not a second chance.
            for (var u : urls) {
                var host = hostOf(u);
                if (host.isEmpty() || seenHosts.contains(host)) continue;
                seenHosts.add(host);
                out.add(u);
            }
        }
        var order = cdnOrderTokens();
        out.sort((a, b) -> Integer.compare(hostRank(hostOf(a), order), hostRank(hostOf(b), order)));
        return out;
    }

    private static String codecsOf(JsonObject stream) {
        return stream != null && stream.has("codecs") && !stream.get("codecs").isJsonNull()
                ? stream.get("codecs").getAsString() : "";
    }

    private static int bandwidthOf(JsonObject stream) {
        if (!stream.has("bandwidth") || stream.get("bandwidth").isJsonNull()) return 0;
        try {
            return stream.get("bandwidth").getAsInt();
        } catch (Exception e) {
            return 0;
        }
    }

    /** {@code id/codecs@bandwidth} of every stream, in preference order. */
    private static String describeStreams(List<JsonObject> streams) {
        var sb = new StringBuilder();
        for (var s : streams) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s.has("id") && !s.get("id").isJsonNull() ? s.get("id").getAsString() : "?");
            sb.append('/').append(codecsOf(s)).append('@').append(bandwidthOf(s));
        }
        return sb.toString();
    }

    /** Never throws: a logging helper that breaks the caller is worse than a missing field. */
    private static boolean boolOf(JsonObject obj, String key) {
        return obj != null && obj.has(key) && obj.get(key).isJsonPrimitive() && obj.get(key).getAsBoolean();
    }

    private static String hostOf(String url) {
        try {
            var host = URI.create(url).getHost();
            return host == null ? "" : host;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Host preference, lower first, from the {@code cdnOrder} tokens (first match wins); unmatched hosts keep server
     * order after every match. The P2P edge is always last: {@code mcdn.bilivideo.cn} contains {@code bilivideo}, so a
     * "prefer bilivideo" entry would otherwise rank it among the CDNs this option exists to avoid.
     */
    private static int hostRank(String host, List<String> order) {
        if (host.startsWith("mcdn.") || host.contains(".mcdn."))
            return order.size() + 1;
        for (var i = 0; i < order.size(); i++) {
            if (host.contains(order.get(i)))
                return i;
        }
        return order.size();
    }

    /**
     * The configured CDN preference, or the built-in one when the option is empty: an empty list would tie every host
     * at rank 0 and hand the P2P edge the first slot; the default mirrors the shipped config value.
     */
    static List<String> cdnOrderTokens() {
        var configured = tokens(configText(c -> c.cdnOrder));
        return configured.isEmpty() ? tokens(DEFAULT_CDN_ORDER) : configured;
    }

    /**
     * Look up a video by AV number (aid), returning the BV number; null when it cannot be found.
     */
    public static String fetchBvidByAid(long aid) {
        try {
            String apiUrl = "https://api.bilibili.com/x/web-interface/view?aid=" + aid;
            // Must go through apiRequest: it is the only place that applies REQUEST_TIMEOUT (this call runs on the loading thread).
            HttpRequest request = apiRequest(apiUrl, null).GET().build();
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
            if (json.get("code").getAsInt() != 0) return null;

            JsonObject data = json.getAsJsonObject("data");
            if (data == null || !data.has("bvid")) return null;
            String bvid = data.get("bvid").getAsString();
            return bvid == null || bvid.isEmpty() ? null : bvid;

        } catch (Exception e) {
            return null;
        }
    }

    public static MusicMediaResult getMusicMediaResultFromBV(String BV) {
        // The failure reason must be recorded with the identifier that came in (bare identifier with -pN = MusicSource#getIdentifier()).
        var failId = BV;
        // Lookups use the bare number, but the source identifier keeps -p<N>; the duration must come from that part in pages[] (the top-level one is the whole video).
        var page = pageOf(BV);
        var idWithPage = BV.strip();
        BV = stripPage(BV);
        try {
            String apiUrl = "https://api.bilibili.com/x/web-interface/view?bvid=" + BV;
            HttpRequest request = apiRequest(apiUrl, null).GET().build();
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // [logging only] Without this the log only shows "No available loaders found": no way to tell network / risk control / API error apart.
                dev.felnull.imp.client.music.link.LinkDebug.log("BiliBili view(media) HTTP", "bv", BV,
                        "httpStatus", response.statusCode(), "cookieSent", !configCookie().isEmpty(),
                        "bodyHead", response.body().substring(0, Math.min(160, response.body().length())));
                // [send the status code to the UI] 412 = risk control, reported as one actionable suggestion; others as they are.
                MusicLoadFailure.noteReason(failId, httpFailReason(
                        MusicLoadFailure.text("imp.reason.bilibili.whatView"), response.statusCode()));
                return null;
            }

            JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
            if (json.get("code").getAsInt() != 0) {
                dev.felnull.imp.client.music.link.LinkDebug.log("BiliBili view(media) code", "bv", BV,
                        "code", json.get("code").getAsString(),
                        "message", json.has("message") ? json.get("message").getAsString() : "?");
                MusicLoadFailure.noteReason(failId, MusicLoadFailure.text("imp.reason.bilibili.apiSaid",
                        json.has("message") && !json.get("message").isJsonNull()
                                ? json.get("message").getAsString()
                                : ("code " + json.get("code").getAsString())));
                return null;
            }

            JsonObject data = json.getAsJsonObject("data");

            String title = data.get("title").getAsString();
            // Part: for multi-part videos with a valid page number, the name uses that part's `part` (the top-level title is the collection name shared by all parts)
            var partPages = data.getAsJsonArray("pages");
            if (partPages != null && partPages.size() > 1 && page >= 1 && page <= partPages.size()) {
                var partObj = partPages.get(page - 1).getAsJsonObject();
                // Guard against JsonNull: has() returns true for "part": null and getAsString() would throw,
                // and that exception is swallowed by the broad catch below ⇒ the whole music entry fails to load.
                if (partObj.has("part") && !partObj.get("part").isJsonNull()
                        && !partObj.get("part").getAsString().isBlank())
                    title = partObj.get("part").getAsString();
            }
            String pic = data.get("pic").getAsString();
            long duration = data.get("duration").getAsLong();
            // Multi-part video: each part's duration is in pages[], while the top-level duration is the whole total
            var pagesArr = data.getAsJsonArray("pages");
            if (pagesArr != null && pagesArr.size() > 1 && page >= 1 && page <= pagesArr.size()) {
                var pageObj = pagesArr.get(page - 1).getAsJsonObject();
                if (pageObj.has("duration") && !pageObj.get("duration").isJsonNull())
                    duration = pageObj.get("duration").getAsLong();
            }

            // [分P] The `view` response is fetched here anyway (the submission list carries no part count): store the
            // part list so the HUD can label P1/2 and "展开分P" needs no extra request. One-element list = nothing to expand.
            var parts = BiliBiliParts.partsOf(data);
            if (!parts.isEmpty()) {
                BiliBiliArcTags.getInstance().rememberParts(BV, parts);
                BiliBiliArcTags.getInstance().flush();
                // …and the server gets it too: with 「多P 按一条算」 it is the server that walks from one part to the
                // next, while the video stays a single playlist entry.
                BiliBiliParts.report(BV, parts);
            }

            List<String> authors = new ArrayList<>();
            JsonObject owner = data.getAsJsonObject("owner");
            authors.add(owner.get("name").getAsString());

            JsonArray staff = data.getAsJsonArray("staff");
            if (staff != null) {
                for (JsonElement elem : staff) {
                    JsonObject staffObj = elem.getAsJsonObject();
                    authors.add(staffObj.get("name").getAsString());
                }
            }

            // Cover: the API returns http://i*.hdslb.com/... — normalize to https (plain http is blocked on some networks). Whether to use it at all is config.
            ImageInfo imageInfo = ImageInfo.EMPTY;
            if (IamMusicPlayer.getConfig().bilibiliConfig.loadVideoImage) {
                String picUrl = pic;
                // When the config is on and this part has a first-frame image, use it as the cover (the API has no per-part cover)
                if (IamMusicPlayer.getConfig().bilibiliConfig.usePageFirstFrame
                        && pagesArr != null && pagesArr.size() > 1
                        && page >= 1 && page <= pagesArr.size()) {
                    var frame = pagesArr.get(page - 1).getAsJsonObject();
                    // Guard against JsonNull: has() is still true for `"first_frame": null` and getAsString() would throw
                    if (frame.has("first_frame") && !frame.get("first_frame").isJsonNull()
                            && !frame.get("first_frame").getAsString().isBlank())
                        picUrl = frame.get("first_frame").getAsString();
                }
                imageInfo = new ImageInfo(ImageInfo.ImageType.URL, picUrl.replaceFirst("^http://", "https://"));
            }
            MusicSource source = new MusicSource("bilibili", idWithPage, duration * 1000);
            return new MusicMediaResult(source, imageInfo, title, String.join("、", authors));

        } catch (Exception e) {
            dev.felnull.imp.client.music.link.LinkDebug.log("BiliBili view(media) EX", "bv", BV,
                    "ex", e.getClass().getName() + ": " + e.getMessage());
            MusicLoadFailure.noteReason(failId, fetchFailReason(e));
            return null;
        }
    }

    /** 412 = Bilibili's **risk control** stopped this request; resolved through the language files. */
    public static String reason412() {
        return MusicLoadFailure.text("imp.reason.bilibili.http412");
    }

    /** HTTP status code → a one-line understandable reason (for the red text in the UI; keep it short: it is a single line at the bottom of the screen). */
    public static String httpFailReason(String what, int status) {
        return status == 412 ? reason412() : MusicLoadFailure.text("imp.reason.bilibili.http", what, status);
    }

    /**
     * Address-fetch exception → a one-line reason for the UI, chosen by keyword from the {@code fetchAudioUrl} messages
     * ({@code "Bilibili playurl HTTP …"} / {@code "DASH stream not available"} / {@code "No audio stream found"} /
     * {@code "Bilibili API error: …"}).
     */
    public static String fetchFailReason(Throwable e) {
        var msg = String.valueOf(e.getMessage());
        if (msg.contains("412"))
            return reason412();
        if (msg.contains("DASH"))
            return MusicLoadFailure.text("imp.reason.bilibili.noDashAudio");
        if (msg.contains("No audio stream"))
            return MusicLoadFailure.text("imp.reason.bilibili.noAudioStream");
        if (msg.contains("Failed to get video info"))
            return MusicLoadFailure.text("imp.reason.bilibili.videoGone");
        if (msg.contains("API error"))
            return MusicLoadFailure.text("imp.reason.bilibili.apiError",
                    trimReason(msg.replace("Bilibili API error: ", "")));
        return MusicLoadFailure.text("imp.reason.bilibili.resolveFailed",
                e.getClass().getSimpleName(), trimReason(msg));
    }

    /** The reason must be short too: it is a single line at the bottom of the screen, and an over-long one gets cut off or smears the UI. */
    private static String trimReason(String s) {
        if (s == null)
            return "?";
        s = s.strip();
        return s.length() <= 40 ? s : s.substring(0, 40) + "…";
    }

    public static Lyric fetchLyrics(String bvid, String cookie) throws IOException, InterruptedException {
        if (cookie == null || cookie.isEmpty()) {
            // Without a line here, "no subtitle" from a logged-out client looks identical to "this video has none".
            LinkDebug.log("BiliBili subtitle skip", "bv", bvid, "reason", "cookieEmpty");
            return Lyric.EMPTY;
        }
        List<Pair<String, String>> rawLyrics = fetchRawLyrics(bvid, cookie);
        if (rawLyrics.isEmpty()) {
            return Lyric.EMPTY;
        }
        return choiceLyric(rawLyrics);
    }

    private static List<Pair<String, String>> fetchRawLyrics(String identifier, String cookie) throws IOException, InterruptedException {
        List<Pair<String, String>> result = new ArrayList<>();
        // The part suffix (`BV…-p2`) matters twice: the lookup needs the stripped video number, and the cid must be the part's own (the top-level cid is part 1's).
        var page = pageOf(identifier);
        var bvid = stripPage(identifier);
        if (bvid == null || bvid.isBlank())
            return result;

        String viewUrl = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
        HttpRequest.Builder viewReqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(viewUrl))
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.bilibili.com/");
        if (cookie != null && !cookie.isEmpty()) {
            viewReqBuilder.header("Cookie", cookie);
        }
        HttpResponse<String> viewResp = client().send(viewReqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (viewResp.statusCode() != 200) return result;

        JsonObject viewJson = JsonParser.parseString(viewResp.body()).getAsJsonObject();
        if (viewJson.get("code").getAsInt() != 0) return result;

        JsonObject data = viewJson.getAsJsonObject("data");
        long aid = data.get("aid").getAsLong();
        long cid = data.get("cid").getAsLong();
        // The asked-for part's cid (see the note above); out-of-range falls back to the top-level cid = part 1.
        var lyricPages = data.getAsJsonArray("pages");
        if (lyricPages != null && lyricPages.size() > 1 && page >= 1 && page <= lyricPages.size()) {
            var pageObj = lyricPages.get(page - 1).getAsJsonObject();
            if (pageObj.has("cid") && !pageObj.get("cid").isJsonNull())
                cid = pageObj.get("cid").getAsLong();
        }

        String wbiUrl = "https://api.bilibili.com/x/player/wbi/v2?aid=" + aid + "&cid=" + cid;
        HttpRequest.Builder wbiReqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(wbiUrl))
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.bilibili.com/");
        if (cookie != null && !cookie.isEmpty()) {
            wbiReqBuilder.header("Cookie", cookie);
        }
        HttpResponse<String> wbiResp = client().send(wbiReqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (wbiResp.statusCode() != 200) {
            LinkDebug.log("BiliBili subtitle HTTP", "bv", bvid, "page", page,
                    "httpStatus", wbiResp.statusCode(),
                    "bodyHead", wbiResp.body().substring(0, Math.min(160, wbiResp.body().length())));
            return result;
        }

        JsonObject wbiJson = JsonParser.parseString(wbiResp.body()).getAsJsonObject();
        if (wbiJson.get("code").getAsInt() != 0) {
            LinkDebug.log("BiliBili subtitle code", "bv", bvid, "page", page,
                    "code", wbiJson.has("code") ? wbiJson.get("code").getAsString() : "?",
                    "message", wbiJson.has("message") ? wbiJson.get("message").getAsString() : "?");
            return result;
        }

        // Only the names are logged, never the value: a cookie is a credential.
        var subData = wbiJson.getAsJsonObject("data");
        LinkDebug.log("BiliBili subtitle", "bv", bvid, "page", page, "httpStatus", wbiResp.statusCode(),
                "cookieNames", cookie.isEmpty() ? "(none)" : BiliBiliCookie.namesOf(cookie),
                // True means B站 is withholding the track list from a request it does not consider logged in.
                "needLoginSubtitle", flagOf(subData, "need_login_subtitle"),
                "trackCount", trackCountOf(subData), "tracks", subtitleTagsOf(subData));

        var subtitles = subtitleArrayOf(subData);
        if (subtitles == null) return result;

        for (JsonElement elem : subtitles) {
            JsonObject sub = elem.getAsJsonObject();
            String lang = sub.get("lan").getAsString();
            // The machine/human choice comes from the config; no fallback on purpose: asking for human only has to end
            // as "no lyrics" instead of quietly handing back a machine transcription.
            var ai = sub.has("ai_type");
            if (!trackAllowed(ai, subtitleTrackMode())) continue;
            String url = sub.get("subtitle_url").getAsString();
            if (url.startsWith("//")) url = "https:" + url;

            // The subtitle JSON lives on `aisubtitle.hdslb.com`; the CDN headers are shared with the audio path
            var subReq = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER)
                    .header("Origin", ORIGIN)
                    .GET()
                    .build();
            HttpResponse<String> subResp = client().send(subReq, HttpResponse.BodyHandlers.ofString());
            if (subResp.statusCode() == 200) {
                result.add(new Pair<>(lang, subResp.body()));
            }
        }
        return result;
    }

    /** The subtitle node, or null when the answer carries none (an empty table is not a failure). */
    private static JsonArray subtitleArrayOf(JsonObject data) {
        if (data == null || !data.has("subtitle") || !data.get("subtitle").isJsonObject()) return null;
        var node = data.getAsJsonObject("subtitle");
        return node.has("subtitles") && node.get("subtitles").isJsonArray() ? node.getAsJsonArray("subtitles") : null;
    }

    private static int trackCountOf(JsonObject data) {
        var arr = subtitleArrayOf(data);
        return arr == null ? 0 : arr.size();
    }

    /** {@code lan} plus its origin ({@code t=ai} for a machine track, {@code t=human} otherwise), for one log line. */
    private static String subtitleTagsOf(JsonObject data) {
        var arr = subtitleArrayOf(data);
        if (arr == null || arr.isEmpty()) return "(none)";
        var sb = new StringBuilder();
        for (var elem : arr) {
            if (sb.length() > 0) sb.append(',');
            var sub = elem.getAsJsonObject();
            sb.append(sub.has("lan") ? sub.get("lan").getAsString() : "?");
            sb.append(sub.has("ai_type") ? "/ai" : "/human");
        }
        return sb.toString();
    }

    private static String flagOf(JsonObject data, String key) {
        if (data == null || !data.has(key) || data.get(key).isJsonNull()) return "?";
        return data.get(key).getAsString();
    }

    private static boolean trackAllowed(boolean ai, dev.felnull.imp.IMPConfig.SubtitleTrack mode) {
        if (mode == null)
            return true;
        return switch (mode) {
            case ALL -> true;
            case HUMAN_ONLY -> !ai;
            case AI_ONLY -> ai;
        };
    }

    /** The configured track filter, falling back to the shipped default where the config cannot be read. */
    static dev.felnull.imp.IMPConfig.SubtitleTrack subtitleTrackMode() {
        try {
            var mode = IamMusicPlayer.getConfig().bilibiliConfig.subtitleTrack;
            if (mode != null)
                return mode;
        } catch (Throwable ignored) {
            // No config (standalone use): the shipped default applies
        }
        return dev.felnull.imp.IMPConfig.SubtitleTrack.ALL;
    }

    /**
     * The language tokens the configured preference asks for, or {@code null} for the built-in order (human original
     * language first, then Chinese). A token matches by containment: {@code zh} takes {@code zh-CN}/{@code ai-zh}.
     */
    private static List<String> subtitleLangTokens() {
        var lang = subtitleLangMode();
        if (lang == null || lang == dev.felnull.imp.IMPConfig.SubtitleLang.BUILTIN)
            return null;
        return List.of(lang.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** The configured preference, falling back to the shipped default where the config cannot be read. */
    static dev.felnull.imp.IMPConfig.SubtitleLang subtitleLangMode() {
        try {
            var lang = IamMusicPlayer.getConfig().bilibiliConfig.subtitleLang;
            if (lang != null)
                return lang;
        } catch (Throwable ignored) {
            // No config (standalone use): the shipped default applies
        }
        return dev.felnull.imp.IMPConfig.SubtitleLang.BUILTIN;
    }

    /** The configured 「刷新」 scope, falling back to the shipped default where the config cannot be read. */
    static dev.felnull.imp.IMPConfig.RefreshScope refreshScopeMode() {
        try {
            var scope = IamMusicPlayer.getConfig().bilibiliConfig.refreshScope;
            if (scope != null)
                return scope;
        } catch (Throwable ignored) {
            // No config (standalone use): the shipped default applies
        }
        return dev.felnull.imp.IMPConfig.RefreshScope.AUTO;
    }

    private static List<String> tokens(String raw) {
        var out = new ArrayList<String>();
        if (raw == null || raw.isBlank())
            return out;
        for (var part : raw.split(",")) {
            var token = part.strip().toLowerCase(java.util.Locale.ROOT);
            if (!token.isEmpty())
                out.add(token);
        }
        return out;
    }

    /** One config string, or {@code null} when the config cannot be read or the getter throws for a null field. */
    private static String configText(java.util.function.Function<dev.felnull.imp.IMPConfig.BilibiliConfig, String> get) {
        try {
            var value = get.apply(IamMusicPlayer.getConfig().bilibiliConfig);
            return value == null ? null : value.strip();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Picks the lyric line and the translation line; the translation comes from {@code bilibiliConfig.subtitleTrans},
     * never unconditionally as "the first track in another language" (that put an English line under Chinese lyrics).
     */
    public static Lyric choiceLyric(List<Pair<String, String>> lyricsByLanguage) {
        return choiceLyric(lyricsByLanguage, subtitleTransMode());
    }

    /** The configured translation mode, with a standalone fallback: the config cannot be read outside the game. */
    static dev.felnull.imp.IMPConfig.SubtitleTrans subtitleTransMode() {
        try {
            var mode = IamMusicPlayer.getConfig().bilibiliConfig.subtitleTrans;
            if (mode != null)
                return mode;
        } catch (Throwable ignored) {
            // No config (standalone self-test): the shipped default applies
        }
        return dev.felnull.imp.IMPConfig.SubtitleTrans.SMART;
    }

    /**
     * {@link #choiceLyric(List)} with the translation mode passed in, so every mode is testable without a game.
     */
    static Lyric choiceLyric(List<Pair<String, String>> lyricsByLanguage,
                             dev.felnull.imp.IMPConfig.SubtitleTrans mode) {
        if (lyricsByLanguage == null || lyricsByLanguage.isEmpty()) {
            return Lyric.EMPTY;
        }

        // A configured language preference decides the order outright; without one the built-in order below applies.
        var ordered = orderByLangPreference(lyricsByLanguage, subtitleLangTokens());

        Pair<String, String> main = null;
        Pair<String, String> translation = null;
        if (ordered == null) {
            for (Pair<String, String> p : lyricsByLanguage) {
                var human = !p.getA().contains("ai");
                var chinese = isChinese(p.getA());
                if (main == null && human && !chinese) {
                    main = p;                       // a human original-language track, as before
                    continue;
                }
                if (translation == null && human && chinese) {
                    translation = p;                // a human Chinese translation
                }
            }
            for (Pair<String, String> p : lyricsByLanguage) {
                if (main == null && isChinese(p.getA()))
                    main = p;                       // AI Chinese is the content when nothing human exists
            }
            // The remaining candidates in list order; a configured preference keeps them in the order it asked for.
            var rest = new ArrayList<Pair<String, String>>();
            for (Pair<String, String> p : lyricsByLanguage) {
                if (p != main)
                    rest.add(p);
            }
            ordered = rest;
        } else {
            main = ordered.get(0);
        }
        if (main == null)
            main = lyricsByLanguage.get(0);

        translation = pickTranslation(lyricsByLanguage, ordered, main, translation, mode);

        Float2ObjectSortedMap<String> originalMap = parseLyricJson(main.getB());
        Float2ObjectSortedMap<String> transMap = translation == null ? null : parseLyricJson(translation.getB());

        // [Diagnostic] Which track became the lyric matters when a video offers both a human upload and B站's AI transcript.
        if (LinkDebug.shouldLogThrottled("bililyric:pick:" + main.getA()))
            LinkDebug.log("BiliBili lyric pick", "main", main.getA() + (main.getA().contains("ai") ? "(AI)" : "(human)"),
                    "trans", translation == null ? "(none)" : translation.getA(),
                    "tracks", lyricsByLanguage.size());

        if (originalMap == null) {
            // One unreadable line (missing/non-numeric `from`) fails the whole track, so it must not pass silently.
            LinkDebug.log("BiliBili subtitle parse failed", "lang", main.getA(),
                    "bytes", main.getB() == null ? -1 : main.getB().length());
            return Lyric.EMPTY;
        }

        return new Lyric(originalMap, transMap);
    }

    /**
     * The tracks in the order the language preference asks for, or {@code null} when no token matched (the caller then
     * uses the built-in order). A token matches by containment: {@code zh} takes {@code zh-CN}/{@code ai-zh}.
     */
    private static List<Pair<String, String>> orderByLangPreference(List<Pair<String, String>> all, List<String> tokens) {
        if (tokens == null || tokens.isEmpty())
            return null;
        var ordered = new ArrayList<Pair<String, String>>();
        for (var token : tokens) {
            // Human uploads first inside one language: B站's AI transcript is the same language tag but drops sentences.
            for (var humanPass : new boolean[] {true, false}) {
                for (var p : all) {
                    if (ordered.contains(p))
                        continue;
                    var lan = p.getA();
                    if (lan == null || !lan.toLowerCase(java.util.Locale.ROOT).contains(token))
                        continue;
                    if (!lan.contains("ai") != humanPass)
                        continue;
                    ordered.add(p);
                }
            }
        }
        return ordered.isEmpty() ? null : ordered;
    }

    /**
     * The translation line for the chosen main line, per the configured {@link dev.felnull.imp.IMPConfig.SubtitleTrans}.
     *
     * @param ordered          the tracks a preference did not use as the main line, in the configured order; with no
     *                         preference configured this is simply the list order
     * @param humanTranslation a human Chinese track found while picking the main line ({@code null} = none); kept when
     *                         it fits the mode, because human subtitles beat AI ones
     */
    private static Pair<String, String> pickTranslation(List<Pair<String, String>> all,
                                                        List<Pair<String, String>> ordered,
                                                        Pair<String, String> main,
                                                        Pair<String, String> humanTranslation,
                                                        dev.felnull.imp.IMPConfig.SubtitleTrans mode) {
        if (mode == null)
            mode = dev.felnull.imp.IMPConfig.SubtitleTrans.SMART;

        return switch (mode) {
            // 智能：中文正文不要外语翻译；外语正文仍配它的中文翻译
            case SMART -> isChinese(main.getA()) ? null : other(ordered, main, humanTranslation, "zh", true);
            // 明确指定要哪种外语作翻译行（中文视频也能看英/日文字幕）
            case EN -> other(ordered, main, null, "en", false);
            case JA -> other(ordered, main, null, "ja", false);
            // 老行为：第一条不同语言的轨道
            case FIRST -> other(ordered, main, humanTranslation, null, true);
        };
    }

    /**
     * The translation track: {@code preferred} when it fits the requested language, otherwise the first track that does.
     *
     * @param ordered            candidate tracks in list order
     * @param preferred          rejected when it is the main line itself (drawing one text twice reads as a bug)
     * @param langToken          language tag the track must contain ({@code null} = any other language)
     * @param excludeMainContent also rejects a track whose text equals the main line's — B站 sometimes lists the same
     *                           content under two language tags
     */
    private static Pair<String, String> other(List<Pair<String, String>> ordered, Pair<String, String> main,
                                              Pair<String, String> preferred, String langToken,
                                              boolean excludeMainContent) {
        if (preferred != null && preferred != main
                && (langToken == null || (preferred.getA() != null && preferred.getA().contains(langToken)))
                && (!excludeMainContent || !preferred.getB().equals(main.getB())))
            return preferred;
        return firstOther(ordered, main, langToken, excludeMainContent);
    }

    /**
     * The first track that is not {@code main} whose tag contains {@code langToken} ({@code null} = any language).
     *
     * @param excludeMainContent also rejects a track whose text equals the main line's — B站 sometimes lists the same
     *                           content under two language tags
     */
    private static Pair<String, String> firstOther(List<Pair<String, String>> ordered, Pair<String, String> main,
                                                   String langToken, boolean excludeMainContent) {
        for (Pair<String, String> p : ordered) {
            if (p == main)
                continue;
            if (langToken != null && (p.getA() == null || !p.getA().contains(langToken)))
                continue;
            if (excludeMainContent && p.getB().equals(main.getB()))
                continue;
            return p;
        }
        return null;
    }

    /** Language tags that mean "Chinese" in B站's subtitle list ({@code zh}, {@code zh-CN}, {@code ai-zh}, …). */
    private static boolean isChinese(String lan) {
        return lan != null && lan.contains("zh");
    }

    private static Float2ObjectSortedMap<String> parseLyricJson(String jsonContent) {
        if (jsonContent == null || jsonContent.isEmpty()) return null;

        try {
            JsonObject root = JsonParser.parseString(jsonContent).getAsJsonObject();
            JsonArray body = root.getAsJsonArray("body");
            if (body == null || body.isEmpty()) return null;

            Float2ObjectSortedMap<String> map = new Float2ObjectRBTreeMap<>();
            for (JsonElement elem : body) {
                JsonObject line = elem.getAsJsonObject();
                float from = line.get("from").getAsFloat();
                String content = line.get("content").getAsString();
                map.put(from, content);
            }
            return map.isEmpty() ? null : map;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The mid of the UP who uploaded a video ({@code data.owner.mid}), {@code -1} when unreadable: a mid survives an UP
     * renaming themselves, which is what the 「刷新」 claim stores.
     */
    public static long fetchOwnerMid(String bvid) throws Exception {
        var json = request("https://api.bilibili.com/x/web-interface/view?bvid=" + bvid);
        if (json == null || !json.has("code") || json.get("code").getAsInt() != 0)
            return -1L;
        var data = json.getAsJsonObject("data");
        var owner = data == null || !data.has("owner") ? null : data.getAsJsonObject("owner");
        return owner == null || !owner.has("mid") ? -1L : owner.get("mid").getAsLong();
    }

    /**
     * Every mid credited on one video: the uploader ({@code data.owner.mid}) plus the members of a 联合投稿
     * ({@code data.staff[]}), since a 联合投稿 names the uploader first — so the caller samples several videos.
     *
     * @return the credited mids (uploader first, no duplicates); empty when the response cannot be read
     */
    public static List<Long> fetchCreditedMids(String bvid) throws Exception {
        var json = request("https://api.bilibili.com/x/web-interface/view?bvid=" + bvid);
        if (json == null || !json.has("code") || json.get("code").getAsInt() != 0 || !json.has("data"))
            return List.of();
        var data = json.getAsJsonObject("data");
        var out = new ArrayList<Long>(2);
        var owner = data.has("owner") && data.get("owner").isJsonObject() ? data.getAsJsonObject("owner") : null;
        if (owner != null && owner.has("mid"))
            out.add(owner.get("mid").getAsLong());
        if (data.has("staff") && data.get("staff").isJsonArray()) {
            for (var e : data.getAsJsonArray("staff")) {
                if (e == null || !e.isJsonObject())
                    continue;
                var s = e.getAsJsonObject();
                if (!s.has("mid"))
                    continue;
                var mid = s.get("mid").getAsLong();
                if (!out.contains(mid))
                    out.add(mid);
            }
        }
        return out;
    }

    public static List<String> fetchFavBvids(long fid) throws Exception {
        List<String> list = new ArrayList<>();
        for (var media : fetchFavMedia(fid))
            list.add(media.bvid());
        return list;
    }

    /**
     * 收藏夹内容：bvid + 投稿时间。响应里没有 `pubtime` 时给 0 —— 调用方自己决定要不要逐个视频补。
     */
    public static List<FavMedia> fetchFavMedia(long fid) throws Exception {
        List<FavMedia> list = new ArrayList<>();
        int pn = 1;

        while (true) {

            String url = "https://api.bilibili.com/x/v3/fav/resource/list"
                    + "?media_id=" + fid
                    + "&pn=" + pn
                    + "&ps=20"
                    + "&order=mtime"
                    + "&platform=web";

            JsonObject json = request(url);

            if (json.get("code").getAsInt() != 0)
                throw new RuntimeException(json.toString());

            JsonObject data = json.getAsJsonObject("data");
            JsonArray medias = data.getAsJsonArray("medias");

            if (medias == null || medias.isEmpty())
                break;

            for (JsonElement e : medias) {
                var o = e.getAsJsonObject();
                long pub = num(o, "pubtime") * 1000L;
                var upper = o.has("upper") && o.get("upper").isJsonObject() ? o.getAsJsonObject("upper") : null;
                list.add(new FavMedia(str(o, "bvid"), pub, str(o, "title"),
                        upper == null ? "" : str(upper, "name"), str(o, "cover"), num(o, "duration")));
            }

            pn++;

            if(!data.get("has_more").getAsBoolean())
                break;
        }

        return list;
    }

    /** 收藏夹列表的一条：B站 的列表接口自带标题/作者/封面/时长/投稿时间，导入时不必再逐首问。 */
    public record FavMedia(String bvid, long pubtimeMs, String title, String author, String cover, long durationSec) {
    }

    private static String str(JsonObject o, String k) {
        if (o == null || !o.has(k))
            return "";
        var e = o.get(k);
        return e == null || e.isJsonNull() || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString() ? "" : e.getAsString();
    }

    private static long num(JsonObject o, String k) {
        if (o == null || !o.has(k))
            return 0L;
        var e = o.get(k);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive())
            return 0L;
        try {
            return e.getAsLong();
        } catch (Throwable t) {
            return 0L;
        }
    }

    public static VideoInfo fetchVideo(String bvid) throws Exception {
        String url = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;

        JsonObject json = request(url);

        if (json.get("code").getAsInt() != 0)
            return null;

        JsonObject v = json.getAsJsonObject("data");

        return new VideoInfo(
                bvid,
                v.get("title").getAsString(),
                v.get("pic").getAsString(),
                v.getAsJsonObject("owner").get("name").getAsString(),
                v.get("duration").getAsInt()
        );
    }

    private static JsonObject request(String url) throws Exception {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://www.bilibili.com/")
                .GET();
        var cookie = IamMusicPlayer.getConfig().bilibiliConfig.bilibiliCookie;
        if(cookie != null && !cookie.isEmpty())
            builder = builder.header("Cookie", cookie);
        HttpRequest request = builder.build();

        HttpResponse<String> response =
                client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        return GSON.fromJson(response.body(), JsonObject.class);
    }

    public static FavInfo fetchFavInfo(long fid) throws Exception {
        String url = "https://api.bilibili.com/x/v3/fav/folder/info?media_id=" + fid;

        JsonObject json = request(url);

        if (json.get("code").getAsInt() != 0)
            throw new RuntimeException(json.toString());

        JsonObject data = json.getAsJsonObject("data");

        return new FavInfo(
                data.get("title").getAsString(),
                data.get("cover").getAsString(),
                data.get("media_count").getAsInt(),
                data.getAsJsonObject("upper").get("name").getAsString()
        );
    }

    public record FavInfo(
            String title,
            String cover,
            int count,
            String author
    ) {}

    public record VideoInfo(
            String bvid,
            String title,
            String cover,
            String author,
            int duration
    ) {}
}
