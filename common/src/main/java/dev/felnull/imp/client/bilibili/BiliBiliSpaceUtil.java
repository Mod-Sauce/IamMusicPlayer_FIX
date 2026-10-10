package dev.felnull.imp.client.bilibili;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.client.music.MusicLoadFailure;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "UP 主页" data. {@code x/space/wbi/arc/search} needs a WBI signature **and** the anonymous cookie trio (unsigned ⇒ {@code -352}, signed without cookies ⇒ HTTP 412); only the parent zone names from {@code tlist} are trustworthy.
 */
public final class BiliBiliSpaceUtil {
    private BiliBiliSpaceUtil() {
    }

    private static final String ARC_SEARCH = "https://api.bilibili.com/x/space/wbi/arc/search";
    private static final String KEYWORD_ARCHIVES = "https://api.bilibili.com/x/series/recArchivesByKeywords";
    private static final String CARD = "https://api.bilibili.com/x/web-interface/card";
    private static final String SEASONS_SERIES = "https://api.bilibili.com/x/polymer/web-space/seasons_series_list";
    private static final String SEASONS_ARCHIVES = "https://api.bilibili.com/x/polymer/web-space/seasons_archives_list";

    /** Bilibili caps the submission page size at 30. */
    public static final int MAX_PAGE_SIZE = 30;
    /**
     * Prefix of the space identifier used by the link/import layer: {@code space:<mid>} — a space (mid) and a favourites folder (fid) are both plain numbers.
     */
    public static final String ID_PREFIX = "space:";
    private static final java.time.Duration REQUEST_TIMEOUT = java.time.Duration.ofSeconds(20);

    /** Parses {@code space:<mid>}; {@code -1} when the identifier is not a space identifier. */
    public static long midOf(String identifier) {
        if (identifier == null)
            return -1;
        var s = identifier.strip();
        if (!s.startsWith(ID_PREFIX))
            return -1;
        try {
            return Long.parseLong(s.substring(ID_PREFIX.length()).strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Prefix of a **favourites folder** in a playlist's {@code Source}: what gets remembered is prefixed, else a folder could not be told from another numeric id later.
     */
    public static final String FAV_PREFIX = "fav:";

    /**
     * The {@code Source} value of an import identifier ({@code space:<mid>} / {@code fav:<fid>} / {@code ""}), written at import and read back by the 「刷新」 key.
     */
    public static String sourceOf(String identifier) {
        if (identifier == null)
            return "";
        var s = identifier.strip();
        var mid = midOf(s);
        if (mid >= 0)
            return ID_PREFIX + mid;
        if (s.startsWith(FAV_PREFIX))
            return favIdOf(s) >= 0 ? s : "";
        // 链接也算数：用户直接粘收藏夹/主页链接时，只认数字会让来源变成空
        var fav = java.util.regex.Pattern.compile("[?&]fid=(\\d+)").matcher(s);
        if (fav.find())
            return FAV_PREFIX + fav.group(1);
        if (s.contains("favlist"))
            return "";   // 收藏夹链接但没有 fid=：宁可空，也不能当成 UP 主页
        var up = java.util.regex.Pattern.compile("space\\.bilibili\\.com/(\\d+)(?![0-9])").matcher(s);
        if (up.find())
            return ID_PREFIX + up.group(1);
        return !s.isEmpty() && s.chars().allMatch(Character::isDigit) ? FAV_PREFIX + s : "";
    }

    /** Parses {@code fav:<fid>}; {@code -1} when it is not a favourites-folder source. */
    public static long favIdOf(String source) {
        if (source == null)
            return -1;
        var s = source.strip();
        if (!s.startsWith(FAV_PREFIX))
            return -1;
        try {
            return Long.parseLong(s.substring(FAV_PREFIX.length()).strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Whether a stored {@code Source} is something the 「刷新」 key can walk again. */
    public static boolean isRefreshable(String source) {
        return midOf(source) >= 0 || favIdOf(source) >= 0;
    }

    /** One video submission of a user ({@code createdMs} = upload time, {@code created}/{@code ctime} as ms). */
    public record SpaceVideo(String bvid, String title, String author, long durationMs, String cover, long createdMs,
                             int typeId, boolean chargingOnly, boolean lesson, boolean livePlayback, boolean cooperation) {
    }

    /** One entry of {@code tlist}: a **parent** zone the user has submissions in. */
    public record ZoneIndex(int tid, String name, int count) {
    }

    /** One page of submissions; {@code zones} is only filled by {@link #fetchVideos} (the 合集 endpoint has no zone index). */
    public record VideoPage(List<SpaceVideo> videos, List<ZoneIndex> zones, int total, int page, int pageSize) {
    }

    /** One of the user's 合集 (up-defined grouping, unrelated to Bilibili's zones). */
    public record Season(long seasonId, String name, int total, String cover) {
    }

    /** The UP's display name and avatar ({@code face} is on {@code i*.hdslb.com}, which the image loader allows). */
    public record UpInfo(String name, String face) {
        public static final UpInfo EMPTY = new UpInfo("", "");
    }

    /**
     * Name **and avatar** of one UP via {@code x/web-interface/card} — the one profile call that works anonymously, while {@code arc/search} answers {@code -352 风控校验失败}.
     */
    public static UpInfo fetchUpInfo(long mid) throws Exception {
        var params = new LinkedHashMap<String, String>();
        params.put("mid", String.valueOf(mid));
        var json = get(CARD, mid, false, params);
        if (!json.has("data") || !json.get("data").isJsonObject())
            return UpInfo.EMPTY;
        var data = json.getAsJsonObject("data");
        var card = data.has("card") && data.get("card").isJsonObject() ? data.getAsJsonObject("card") : data;
        return new UpInfo(stringOf(card, "name"), https(stringOf(card, "face")));
    }

    /** A space API call that failed in a way the UI should explain. */
    public static class SpaceApiException extends IOException {
        private final boolean riskControl;

        public SpaceApiException(String message, boolean riskControl) {
            super(message);
            this.riskControl = riskControl;
        }

        /** True when Bilibili's risk control rejected the request (retry later / log in). */
        public boolean isRiskControl() {
            return riskControl;
        }
    }

    // ------------------------------------------------------------------ submissions

    public static VideoPage fetchVideos(long mid, int page, int pageSize, String order) throws Exception {
        return fetchVideos(mid, page, pageSize, order, null);
    }

    /**
     * One page of the user's video submissions.
     *
     * @param order {@code pubdate} (newest first), {@code click} (most played) or {@code stow} (most favourited)
     * @param tid   optional zone filter ({@code null} = all zones); the value is one of the {@link ZoneIndex#tid}s
     */
    public static VideoPage fetchVideos(long mid, int page, int pageSize, String order, Integer tid) throws Exception {
        var params = new LinkedHashMap<String, String>();
        params.put("mid", String.valueOf(mid));
        params.put("ps", String.valueOf(Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE)));
        params.put("pn", String.valueOf(Math.max(page, 1)));
        params.put("order", order == null || order.isBlank() ? "pubdate" : order);
        params.put("platform", "web");
        if (tid != null)
            params.put("tid", String.valueOf(tid));

        var json = get(ARC_SEARCH, mid, true, params);

        var data = json.getAsJsonObject("data");
        var total = data.getAsJsonObject("page").get("count").getAsInt();
        var list = data.getAsJsonObject("list");

        var zones = new ArrayList<ZoneIndex>();
        if (list.has("tlist") && list.get("tlist").isJsonObject()) {
            for (var entry : list.getAsJsonObject("tlist").entrySet()) {
                var z = entry.getValue().getAsJsonObject();
                zones.add(new ZoneIndex(intOf(z, "tid"), stringOf(z, "name"), intOf(z, "count")));
            }
            // Biggest zone first: that is the order the category list shows them in
            zones.sort(Comparator.comparingInt(ZoneIndex::count).reversed());
        }

        var videos = new ArrayList<SpaceVideo>();
        var vlist = list.getAsJsonArray("vlist");
        if (vlist != null) {
            for (var e : vlist)
                videos.add(videoOf(e.getAsJsonObject(), false));
        }

        return new VideoPage(videos, zones, total, page, pageSize);
    }

    // ------------------------------------------------------------------ 合集 / 系列

    /**
     * Fallback for a refused {@code x/space/wbi/arc/search} ({@code -352} / HTTP 412): **all** of a UP's videos via {@code x/series/recArchivesByKeywords} ({@code keywords} empty, {@code pn=0} = no paging) — but no parent zone.
     */
    public static List<SpaceVideo> fetchAllByKeywords(long mid) throws Exception {
        var params = new LinkedHashMap<String, String>();
        params.put("mid", String.valueOf(mid));
        params.put("keywords", "");
        params.put("pn", "0");
        params.put("orderby", "pubdate");

        var json = get(KEYWORD_ARCHIVES, mid, false, params);
        var archives = json.getAsJsonObject("data").getAsJsonArray("archives");
        var videos = new ArrayList<SpaceVideo>();
        if (archives != null) {
            // Same archive shape the 合集 member endpoint uses, hence the same parser
            for (var e : archives)
                videos.add(videoOf(e.getAsJsonObject(), true));
        }
        return videos;
    }

    /** All of the user's 合集 (paged 20 at a time; every page needs one request). */
    public static List<Season> fetchSeasons(long mid) throws Exception {
        var seasons = new ArrayList<Season>();
        var page = 1;
        var total = Integer.MAX_VALUE;
        while (seasons.size() < total && page <= 100) {
            var params = new LinkedHashMap<String, String>();
            params.put("mid", String.valueOf(mid));
            params.put("page_num", String.valueOf(page));
            params.put("page_size", "20");

            var json = get(SEASONS_SERIES, mid, false, params);
            var lists = json.getAsJsonObject("data").getAsJsonObject("items_lists");
            if (lists.has("page")) {
                var p = lists.getAsJsonObject("page");
                // `total` counts this user's 合集
                total = intOf(p, "total");
            }
            var arr = lists.getAsJsonArray("seasons_list");
            if (arr == null || arr.isEmpty())
                break;
            for (var e : arr) {
                var meta = e.getAsJsonObject().getAsJsonObject("meta");
                seasons.add(new Season(longOf(meta, "season_id"), stringOf(meta, "name"),
                        intOf(meta, "total"), https(stringOf(meta, "cover"))));
            }
            page++;
        }
        return seasons;
    }

    /** One page of the videos inside a 合集, **oldest first** — the order the import tags members in (it takes all). */
    public static VideoPage fetchSeasonVideos(long mid, long seasonId, int page, int pageSize) throws Exception {
        return fetchSeasonVideos(mid, seasonId, page, pageSize, false);
    }

    /**
     * One page of the videos inside a 合集; {@code newestFirst} matters for 「刷新」 — {@code sort_reverse=false} answers **oldest first**, so a walk stopping at the first page with nothing new stops at once.
     */
    public static VideoPage fetchSeasonVideos(long mid, long seasonId, int page, int pageSize, boolean newestFirst) throws Exception {
        var params = new LinkedHashMap<String, String>();
        params.put("mid", String.valueOf(mid));
        params.put("season_id", String.valueOf(seasonId));
        params.put("sort_reverse", newestFirst ? "true" : "false");
        params.put("page_num", String.valueOf(Math.max(page, 1)));
        params.put("page_size", String.valueOf(Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE)));

        var json = get(SEASONS_ARCHIVES, mid, false, params);
        var data = json.getAsJsonObject("data");
        var total = data.has("page") ? intOf(data.getAsJsonObject("page"), "total") : 0;

        var videos = new ArrayList<SpaceVideo>();
        var arr = data.getAsJsonArray("archives");
        if (arr != null) {
            for (var e : arr)
                videos.add(videoOf(e.getAsJsonObject(), true));
        }
        return new VideoPage(videos, List.of(), total, page, pageSize);
    }

    // ------------------------------------------------------------------ parsing

    /**
     * @param archiveShape {@code true} for a 合集 member ({@code duration} in seconds, {@code ctime}, no
     *                     {@code author}/{@code typeid}/{@code is_*}), {@code false} for an {@code arc/search}
     *                     submission ({@code length} as {@code MM:SS}, {@code created}, plus flags)
     */
    private static SpaceVideo videoOf(JsonObject v, boolean archiveShape) {
        var bvid = stringOf(v, "bvid");
        var title = stringOf(v, "title");
        var cover = https(stringOf(v, archiveShape ? "pic" : "pic"));
        var createdMs = longOf(v, archiveShape ? "ctime" : "created") * 1000L;
        var durationMs = archiveShape
                ? longOf(v, "duration") * 1000L
                : parseLengthSeconds(stringOf(v, "length")) * 1000L;
        var typeId = archiveShape ? 0 : intOf(v, "typeid");
        return new SpaceVideo(
                bvid, title, stringOf(v, "author"), durationMs, cover, createdMs, typeId,
                boolOf(v, "is_charging_arc"),
                intOf(v, "is_lesson_video") == 1,
                boolOf(v, "is_live_playback") || intOf(v, "is_live_playback") == 1,
                boolOf(v, "is_union_video") || boolOf(v, "is_cooperation"));
    }

    /** {@code "22:02"} / {@code "1:02:03"} → seconds (Bilibili sends the submission length as a clock string). */
    static long parseLengthSeconds(String length) {
        if (length == null || length.isBlank())
            return 0;
        long total = 0;
        for (var part : length.strip().split(":")) {
            try {
                total = total * 60 + Long.parseLong(part.strip());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return total;
    }

    private static String https(String url) {
        if (url == null)
            return "";
        return url.replaceFirst("^http://", "https://");
    }

    private static String stringOf(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static int intOf(JsonObject o, String key) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static long longOf(JsonObject o, String key) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsLong() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private static boolean boolOf(JsonObject o, String key) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() && o.get(key).getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ transport

    /**
     * One space API request: WBI-signed when the endpoint needs it, plus the space Referer/Origin and the anonymous cookie trio (and the login cookie when configured).
     */
    private static JsonObject get(String endpoint, long mid, boolean wbi, Map<String, String> params) throws Exception {
        var query = wbi ? BiliBiliWbi.signedQuery(params) : plainQuery(params);
        var builder = HttpRequest.newBuilder(URI.create(endpoint + "?" + query))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", BiliBiliUtil.USER_AGENT)
                .header("Referer", "https://space.bilibili.com/" + mid)
                .header("Origin", "https://space.bilibili.com");
        var cookie = BiliBiliUtil.spaceCookie();
        if (!cookie.isEmpty())
            builder.header("Cookie", cookie);

        var response = BiliBiliUtil.client().send(builder.GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        var body = response.body() == null ? "" : response.body();

        if (response.statusCode() != 200) {
            LinkDebug.log("BiliBili space HTTP", "endpoint", endpoint, "httpStatus", response.statusCode(),
                    "bodyHead", body.substring(0, Math.min(160, body.length())));
            var risk = response.statusCode() == 412 || response.statusCode() == 403;
            throw new SpaceApiException(risk
                    ? MusicLoadFailure.text("imp.reason.space.risk", response.statusCode())
                    : MusicLoadFailure.text("imp.reason.space.http", response.statusCode()), risk);
        }

        JsonObject json;
        try {
            json = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            // A risk-control HTML page instead of JSON means the request identity was rejected
            throw new SpaceApiException(MusicLoadFailure.text("imp.reason.space.notJson"), true);
        }

        var code = json.has("code") ? json.get("code").getAsInt() : -1;
        if (code != 0) {
            var message = stringOf(json, "message");
            LinkDebug.log("BiliBili space code", "endpoint", endpoint, "code", code, "message", message);
            throw new SpaceApiException(reasonFor(code, message), code == -352 || code == -412 || code == -403);
        }
        return json;
    }

    /** Bilibili API code → one line a user can act on. */
    private static String reasonFor(int code, String message) {
        if (code == -352)
            return MusicLoadFailure.text("imp.reason.space.risk352");
        if (code == -412)
            return MusicLoadFailure.text("imp.reason.space.blocked412");
        if (code == -404 || code == 22001)
            return MusicLoadFailure.text("imp.reason.space.noUser");
        return MusicLoadFailure.text("imp.reason.space.apiError",
                message == null || message.isBlank() ? ("code " + code) : message);
    }

    private static String plainQuery(Map<String, String> params) {
        var sb = new StringBuilder();
        for (var e : params.entrySet()) {
            if (sb.length() > 0)
                sb.append('&');
            sb.append(BiliBiliWbi.encode(e.getKey())).append('=').append(BiliBiliWbi.encode(e.getValue()));
        }
        return sb.toString();
    }
}
