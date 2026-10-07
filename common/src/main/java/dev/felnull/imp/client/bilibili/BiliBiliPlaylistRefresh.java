package dev.felnull.imp.client.bilibili;

import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.MusicSyncManager;
import dev.felnull.imp.client.music.link.LinkDebug;
import java.util.LinkedHashMap;
import java.util.Map;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicSource;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.networking.IMPPackets;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.networking.existence.BlockEntityExistence;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 「刷新」: re-walk the import source ({@code space:<mid>} / {@code fav:<fid>}) and append only missing songs — never reorder/delete/rename.
 */
public final class BiliBiliPlaylistRefresh {
    private BiliBiliPlaylistRefresh() {
    }

    public enum State {
        IDLE, RUNNING, DONE, FAILED
    }

    /** One video the source has and the playlist does not (B站's own fields, straight from the submissions response). */
    public record NewVideo(String bvid, String title, String author, long durationMs, String cover) {
    }

    private static volatile State state = State.IDLE;
    private static volatile int added;
    private static volatile String error = "";
    private static volatile long finishedAt;
    /** The playlist the last (or running) walk belongs to, so a finished result can be shown on that page. */
    private static volatile UUID playlist;

    /**
     * Which playlist the result belongs to ({@code null} = none); the state is client-wide, so the page **must** check it matches the playlist it shows.
     *
     * @return the uuid the result belongs to, or {@code null}
     */
    @Nullable
    public static UUID playlist() {
        return playlist;
    }

    public static State state() {
        return state;
    }

    public static int added() {
        return added;
    }

    public static String error() {
        return error;
    }

    public static long finishedAt() {
        return finishedAt;
    }

    public static boolean isRunning() {
        return state == State.RUNNING;
    }

    /**
     * Last walk refused: the playlist is bound to **another UP** than its songs' author ({@link #upNameMatches}) — the page then offers 「重绑UP」.
     */
    private static volatile boolean needsRebind;

    public static boolean needsRebind() {
        return needsRebind;
    }

    /** Forgets a finished result (called when the result has been shown long enough). */
    public static void clearResult() {
        if (state != State.RUNNING) {
            state = State.IDLE;
            added = 0;
            error = "";
            finishedAt = 0L;
            playlist = null;
            pubdateOnly = false;
            pubdatesFilled = 0;
        }
    }

    /** 本次走查是「只补投稿时间」（不碰歌单、不需要任何权限）。 */
    private static volatile boolean pubdateOnly;

    public static boolean pubdateOnly() {
        return pubdateOnly;
    }

    /** 最近一次走查补到的投稿时间条数（键上文字用：没新增歌但补到时间时也得有个说得出口的结果）。 */
    private static volatile int pubdatesFilled;

    public static int pubdatesFilled() {
        return pubdatesFilled;
    }

    // ------------------------------------------------------------------ pure parts (self-tested)

    /** The bare B站 video numbers of a playlist — the identity a "already there?" check runs on. */
    public static Set<String> knownBvids(List<Music> musics) {
        var out = new LinkedHashSet<String>();
        if (musics == null)
            return out;
        for (var m : musics) {
            if (!isBiliBili(m))
                continue;
            var bvid = BiliBiliUtil.stripPage(m.getSource().getIdentifier());
            if (bvid != null && !bvid.isBlank())
                out.add(bvid);
        }
        return out;
    }

    /**
     * Whether an entry is a B站 one — compared reversed on purpose: {@code MusicMedia#getName()} is a reverse lookup and answers {@code null} before {@code IMPMusicMedias.init()}.
     */
    private static boolean isBiliBili(Music m) {
        var src = m == null ? null : m.getSource();
        return src != null && src.getLoaderType() != null
                && src.getLoaderType().equals(IMPMusicMedias.BILIBILI.getName());
    }

    /** What of {@code from} the playlist does not have yet, in the source's own order, without duplicates. */
    public static List<NewVideo> pickNew(List<NewVideo> from, Set<String> known) {
        var out = new ArrayList<NewVideo>();
        if (from == null)
            return out;
        var seen = new LinkedHashSet<String>();
        for (var v : from) {
            if (v == null || v.bvid() == null || v.bvid().isBlank())
                continue;
            if (known != null && known.contains(v.bvid()))
                continue;
            if (!seen.add(v.bvid()))
                continue;
            out.add(v);
        }
        return out;
    }

    /** The submissions of an UP as {@link NewVideo} (no request — this is the mapping only). */
    public static List<NewVideo> fromSpace(List<BiliBiliSpaceUtil.SpaceVideo> videos) {
        var out = new ArrayList<NewVideo>();
        if (videos == null)
            return out;
        for (var v : videos) {
            // 投稿时间只有这条链看得见，顺手记下（播放列表页的「投稿日期」排序靠它）
            BiliBiliArcTags.getInstance().rememberPubdate(v.bvid(), v.createdMs());
            out.add(new NewVideo(v.bvid(), v.title(), v.author(), v.durationMs(), v.cover()));
        }
        return out;
    }

    /**
     * Claim rule for playlists imported **before** the source field: name ends with "的投稿" (**without** the space old names carried) and every B站 song shares one non-blank author.
     */
    public static boolean looksLikeUpImport(String name, List<Music> musics) {
        var plName = name == null ? "" : name.strip();
        if (!plName.endsWith("的投稿"))
            return false;
        String author = null;
        int count = 0;
        if (musics == null)
            return false;
        for (var m : musics) {
            if (!isBiliBili(m))
                continue;
            var a = m.getAuthor() == null ? "" : m.getAuthor().strip();
            if (a.isEmpty())
                return false;
            if (author == null)
                author = a;
            else if (!author.equals(a))
                return false;
            count++;
        }
        return count > 0;
    }

    /** One {@link NewVideo} as a playlist entry (the shape the import uses: bvid + length in ms). */
    public static Music toMusic(NewVideo video, boolean loadImage, UUID owner) {
        return new Music(
                UUID.randomUUID(),
                video.title(),
                video.author(),
                new MusicSource(IMPMusicMedias.BILIBILI.getName(), video.bvid(), Math.max(0L, video.durationMs())),
                loadImage && video.cover() != null && !video.cover().isBlank()
                        ? new ImageInfo(ImageInfo.ImageType.URL, video.cover())
                        : ImageInfo.EMPTY,
                owner,
                System.currentTimeMillis());
    }

    // ------------------------------------------------------------------ the walk

    /** 本次走查还缺投稿时间的曲子（裸 bvid）：没见完之前不因"这页没有新歌"提前停。 */
    private static volatile Set<String> wantPubdates = Set.of();

    /** 歌单里没记过投稿时间的 B站 曲子。 */
    static Set<String> pubdateWanted(List<Music> existing) {
        var out = new LinkedHashSet<String>();
        if (existing == null)
            return out;
        var tags = BiliBiliArcTags.getInstance();
        for (var music : existing) {
            var source = music == null ? null : music.getSource();
            if (source == null)
                continue;
            var bvid = BiliBiliUtil.stripPage(source.getIdentifier());
            if (bvid == null || bvid.isBlank())
                continue;
            if (tags.pubdateOf(bvid) <= 0L)
                out.add(bvid);
        }
        return out;
    }

    /** 这批投稿的裸 bvid（分P 后缀要剥掉，标签缓存是按 bvid 存的）。 */
    private static Set<String> bvidsOf(List<BiliBiliSpaceUtil.SpaceVideo> videos) {
        var out = new LinkedHashSet<String>();
        for (var v : videos) {
            var bvid = BiliBiliUtil.stripPage(v.bvid());
            if (bvid != null && !bvid.isBlank())
                out.add(bvid);
        }
        return out;
    }

    /**
     * Starts a refresh (no-op while one is running); {@code existing} = the playlist's songs right now.
     */
    public static void start(UUID playlistId, String source, List<Music> existing) {
        start(playlistId, source, existing, "");
    }

    /**
     * @param playlistName a legacy {@code "<UP 名> 的投稿"} is renamed in the same write; needed **here** too, not only at bind time —
     *                     a playlist bound by an earlier version never goes through the bind path again.
     */
    public static void start(UUID playlistId, String source, List<Music> existing, String playlistName) {
        if (state == State.RUNNING || playlistId == null || !BiliBiliSpaceUtil.isRefreshable(source))
            return;
        // ⚠️ Structural guard: with an **unknown** song list every video the source returns counts as new (a bind
        // clears the client's song cache, so `known` would be 0 and the whole catalogue would be appended).
        if (existing == null) {
            error = "songs not loaded";
            state = State.FAILED;
            finishedAt = System.currentTimeMillis();
            LinkDebug.log("playlist refresh refused", "playlist", playlistId, "why", "song list not loaded");
            return;
        }
        var mid = BiliBiliSpaceUtil.midOf(source);
        var fid = BiliBiliSpaceUtil.favIdOf(source);
        var known = knownBvids(existing);
        // 歌单里没记过投稿时间的曲子：走查要多走几页把它们见完（见 pubdateWanted）
        wantPubdates = pubdateWanted(existing);
        var cfg = IamMusicPlayer.getConfig().bilibiliConfig;
        // 「只补同类」: the scope this playlist allows — `refreshScope` picks the dimension, the playlist's own tags
        // decide whether it is usable (合集 first when the playlist is mostly one, else 分区).
        var scope = resolveScope(cfg.refreshScope, existing);
        var maxPages = cfg.spaceMaxPages;
        var delay = Math.max(0, cfg.spacePageDelayMs);
        var loadImage = cfg.loadVideoImage;
        var owner = Minecraft.getInstance().getGameProfile().getId();

        state = State.RUNNING;
        added = 0;
        pubdatesFilled = 0;
        error = "";
        finishedAt = 0L;
        playlist = playlistId;
        needsRebind = false;
        // [Diagnostic] Says which playlist, from which source, with how many songs already known — and **which zones**
        // the walk is limited to (an empty set means "no zone information", which is refused below).
        LinkDebug.log("playlist refresh start", "playlist", playlistId, "source", source,
                "known", known.size(), "songsInPlaylist", existing == null ? -1 : existing.size(),
                "pubdatesWanted", wantPubdates.size(),
                "zones", scope.zones().isEmpty() ? "(none)" : String.join("/", scope.zones()),
                "season", scope.season().isBlank() ? "(none)" : scope.season());
        var thread = new Thread(() -> {
            var fresh = List.<NewVideo>of();
            var cover = "";
            try {
                if (mid >= 0) {
                    // ⚠️ Before walking anything: is this playlist really bound to the UP that uploaded its songs? The
                    // card request is needed for the cover anyway, so the check is free (see upNameMatches).
                    var up = BiliBiliSpaceUtil.fetchUpInfo(mid);
                    if (!upNameMatches(up.name(), existing)) {
                        needsRebind = true;
                        throw new IllegalStateException("this playlist is bound to " + up.name()
                                + " but its songs are by someone else");
                    }
                    fresh = walkUp(mid, known, scope, maxPages, delay);
                    // 走完这一档仍有曲子没记过投稿时间（它们可能不在合集/分区里）：再走一遍**全部投稿**只为补时间 ——
                    // 结果不并入"要新增的歌"，免得越过用户选的刷新范围。
                    if (!wantPubdates.isEmpty()) {
                        walkWholeUp(mid, known, maxPages, delay);
                        int filled = 0;
                        for (var bvid : wantPubdates)
                            if (BiliBiliArcTags.getInstance().pubdateOf(bvid) > 0L)
                                filled++;
                        pubdatesFilled = filled;
                        LinkDebug.log("playlist refresh pubdates", "wanted", wantPubdates.size(), "filled", filled);
                    }
                    cover = up.face();
                } else {
                    var fav = walkFav(fid, known, delay);
                    fresh = fav.videos();
                    cover = fav.cover();
                    // 收藏夹来源也要补投稿时间：`walkFav` 只走"新歌"，已有的 13 首得靠列表/逐视频补，否则行上一直"未记录"
                    fillPubdatesOfFav(fid, wantPubdates, delay);
                }
            } catch (Throwable t) {
                error = BiliBiliUtil.fetchFailReason(t);
                state = State.FAILED;
                finishedAt = System.currentTimeMillis();
                LinkDebug.log("playlist refresh FAIL", "playlist", playlistId, "source", source, "ex", error);
                return;
            }
            // 走查顺手写下的投稿时间（见 fromSpace）要落盘，否则只在这次会话的内存里
            BiliBiliArcTags.getInstance().flush();
            final var videos = List.copyOf(keepExisting(playlistId, fresh));
            final var finalCover = cover;
            LinkDebug.log("playlist refresh walked", "playlist", playlistId, "source", source,
                    "new", videos.size(), "skippedGone", fresh.size() - videos.size(),
                    "cover", finalCover.isBlank() ? "(none)" : "yes");
            final var rename = legacyNameFixed(playlistName);
            Minecraft.getInstance().execute(() -> send(playlistId, source, videos, finalCover, owner, loadImage, rename));
            added = videos.size();
            state = State.DONE;
            finishedAt = System.currentTimeMillis();
        }, "IMP-BiliBili-Playlist-Refresh");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 只补投稿时间：不碰歌单（不加歌、不改名、不改封面，只需读权限）；有来源走查来源（便宜），没有来源就逐首问 {@code view}。
     */
    public static void startPubdates(UUID playlistId, String source, List<Music> existing) {
        if (state == State.RUNNING || playlistId == null)
            return;
        if (existing == null) {
            error = "songs not loaded";
            state = State.FAILED;
            finishedAt = System.currentTimeMillis();
            return;
        }
        var want = pubdateWanted(existing);
        state = State.RUNNING;
        pubdateOnly = true;
        added = 0;
        pubdatesFilled = 0;
        error = "";
        finishedAt = 0L;
        playlist = playlistId;
        needsRebind = false;
        LinkDebug.log("playlist refresh pubdates start", "playlist", playlistId, "source", source,
                "songsInPlaylist", existing.size(), "wanted", want.size());
        if (want.isEmpty()) {
            state = State.DONE;
            finishedAt = System.currentTimeMillis();
            return;
        }
        var mid = BiliBiliSpaceUtil.midOf(source);
        var fid = BiliBiliSpaceUtil.favIdOf(source);
        var cfg = IamMusicPlayer.getConfig().bilibiliConfig;
        var maxPages = cfg.spaceMaxPages;
        var delay = Math.max(0, cfg.spacePageDelayMs);
        var thread = new Thread(() -> {
            int filled = 0;
            var rest = new LinkedHashSet<>(want);
            try {
                if (mid >= 0)
                    filled += walkPubdateSpace(mid, rest, maxPages, delay);
                else if (fid >= 0)
                    filled += fillFromFavListing(fid, rest);
                filled += fillByVideo(rest, delay);
                BiliBiliArcTags.getInstance().flush();
                added = filled;
                pubdatesFilled = filled;
                state = State.DONE;
                finishedAt = System.currentTimeMillis();
                LinkDebug.log("playlist refresh pubdates", "wanted", want.size(), "filled", filled);
            } catch (Throwable t) {
                error = BiliBiliUtil.fetchFailReason(t);
                added = filled;
                state = State.FAILED;
                finishedAt = System.currentTimeMillis();
                LinkDebug.log("playlist refresh pubdates FAIL", "playlist", playlistId, "ex", error);
            }
        }, "IMP-BiliBili-Pubdates");
        thread.setDaemon(true);
        thread.start();
    }

    /** 翻这个 UP 的投稿把缺时间的曲子补上（每页 30 个，比逐个视频问便宜得多）。 */
    private static int walkPubdateSpace(long mid, Set<String> want, int maxPages, int delay) throws Exception {
        var tags = BiliBiliArcTags.getInstance();
        int filled = 0;
        int cap = maxPages <= 0 ? Integer.MAX_VALUE : maxPages;
        for (int page = 1; page <= cap && !want.isEmpty(); page++) {
            var videos = BiliBiliSpaceUtil.fetchVideos(mid, page, BiliBiliSpaceUtil.MAX_PAGE_SIZE, "pubdate").videos();
            if (videos == null || videos.isEmpty())
                break;
            for (var v : videos) {
                var bvid = BiliBiliUtil.stripPage(v.bvid());
                if (v.createdMs() <= 0L || bvid == null || !want.remove(bvid))
                    continue;
                tags.rememberPubdate(bvid, v.createdMs());
                filled++;
            }
            if (videos.size() < BiliBiliSpaceUtil.MAX_PAGE_SIZE)
                break;
            if (delay > 0)
                Thread.sleep(delay);
        }
        return filled;
    }

    /** 收藏夹来源的「刷新」也要补投稿时间：先吃列表自带的 `pubtime`，剩下的逐个视频问。 */
    private static void fillPubdatesOfFav(long fid, Set<String> wanted, int delay) {
        if (wanted == null || wanted.isEmpty())
            return;
        var rest = new LinkedHashSet<>(wanted);
        int filled = 0;
        try {
            filled += fillFromFavListing(fid, rest);
        } catch (Throwable t) {
            LinkDebug.log("playlist refresh fav listing FAIL", "fid", fid, "ex", BiliBiliUtil.fetchFailReason(t));
        }
        filled += fillByVideo(rest, delay);
        pubdatesFilled = filled;
        LinkDebug.log("playlist refresh pubdates", "wanted", wanted.size(), "filled", filled);
    }

    /** 收藏夹列表本身带 `pubtime` 时直接补（省掉逐个视频的请求）。 */
    private static int fillFromFavListing(long fid, Set<String> want) throws Exception {
        var tags = BiliBiliArcTags.getInstance();
        int filled = 0;
        int withTime = 0;
        for (var media : BiliBiliUtil.fetchFavMedia(fid)) {
            if (media.pubtimeMs() <= 0L)
                continue;
            withTime++;
            var bvid = BiliBiliUtil.stripPage(media.bvid());
            if (bvid == null || !want.remove(bvid))
                continue;
            tags.rememberPubdate(bvid, media.pubtimeMs());
            filled++;
        }
        LinkDebug.log("playlist refresh fav listing", "fid", fid, "withPubtime", withTime, "filled", filled);
        return filled;
    }

    /** 兜底：逐个视频问（`view` 的响应里带 `pubdate`，`BiliBiliParts.fetch` 顺手把它写进标签）。 */
    private static int fillByVideo(Set<String> want, int delay) {
        var tags = BiliBiliArcTags.getInstance();
        int filled = 0;
        for (var bvid : new ArrayList<>(want)) {
            try {
                BiliBiliParts.fetch(bvid);
            } catch (Throwable ignored) {
            }
            if (tags.pubdateOf(bvid) > 0L)
                filled++;
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return filled;
    }

    /**
     * One UP's submissions within the playlist's zones, newest first. Must stay on {@code arc/search} — the zone-less "all in one request" endpoint would append the whole catalogue.
     */
    private static List<NewVideo> walkUp(long mid, Set<String> known, Scope scope, int maxPages, int delay) throws Exception {
        if ("all".equals(scope.kind()))
            return walkWholeUp(mid, known, maxPages, delay);
        if ("season".equals(scope.kind())) {
            try {
                return walkSeason(mid, known, scope.season(), maxPages, delay);
            } catch (IllegalStateException e) {
                // The 合集 is not in this UP's list: usually the playlist is bound to the **wrong UP** — retry with the
                // 分区 dimension before giving up, a playlist may still be walkable that way.
                LinkDebug.log("playlist refresh season miss", "mid", mid, "season", scope.season(), "ex", e.getMessage(),
                        "zones", scope.zones().isEmpty() ? "(none)" : String.join("/", scope.zones()));
                if (scope.zones().isEmpty())
                    throw e;
            }
        }
        if (scope.zones().isEmpty())
            throw new IllegalStateException("no zone information for this playlist");
        // Page 1 does double duty: the zone index (tlist: tid + name) and — when the wanted zones are already covered
        // by it — the newest videos themselves.
        var first = BiliBiliSpaceUtil.fetchVideos(mid, 1, BiliBiliSpaceUtil.MAX_PAGE_SIZE, "pubdate");
        var wanted = new ArrayList<Integer>();
        for (var zone : first.zones()) {
            if (scope.zones().contains(zone.name()))
                wanted.add(zone.tid());
        }
        if (wanted.isEmpty())
            throw new IllegalStateException("the playlist's zones are not among this UP's zones " + scope.zones());
        LinkDebug.log("playlist refresh scope", "mid", mid, "kind", scope.kind(),
                "zones", String.join("/", scope.zones()), "tids", wanted.toString(), "tlist", first.zones().size());
        var out = new ArrayList<NewVideo>();
        var seen = new LinkedHashSet<String>();
        var cap = maxPages <= 0 ? Integer.MAX_VALUE : maxPages;
        for (var tid : wanted) {
            var zoneName = "";
            for (var z : first.zones())
                if (z.tid() == tid)
                    zoneName = z.name();
            var pages = 0;
            var seenInZone = 0;
            for (int page = 1; page <= cap; page++) {
                var got = BiliBiliSpaceUtil.fetchVideos(mid, page, BiliBiliSpaceUtil.MAX_PAGE_SIZE, "pubdate", tid);
                pages++;
                seenInZone += got.videos().size();
                seen.addAll(bvidsOf(got.videos()));
                var fresh = pickNew(fromSpace(got.videos()), known);
                out.addAll(fresh);
                // Nothing new on this page of this zone: everything older in it is in the playlist as well ——
                // 除非还有曲子缺投稿时间，那就得继续往下走
                if (fresh.isEmpty() && seen.containsAll(wantPubdates))
                    break;
                if (got.videos().size() < BiliBiliSpaceUtil.MAX_PAGE_SIZE)
                    break;
                if (delay > 0)
                    Thread.sleep(delay);
            }
            // [Diagnostic] The API's own total for this zone, the pages walked and the videos seen.
            LinkDebug.log("playlist refresh zone", "mid", mid, "zone", zoneName, "tid", tid,
                    "apiTotal", first.zones().stream().filter(z -> z.tid() == tid).mapToInt(z -> z.count()).findFirst().orElse(-1),
                    "pages", pages, "videosSeen", seenInZone);
            if (delay > 0)
                Thread.sleep(delay);
        }
        return out;
    }

    /** Scope {@code all}: the UP's whole submissions. */
    private static List<NewVideo> walkWholeUp(long mid, Set<String> known, int maxPages, int delay) throws Exception {
        var out = new ArrayList<NewVideo>();
        var seen = new LinkedHashSet<String>();
        var cap = maxPages <= 0 ? Integer.MAX_VALUE : maxPages;
        for (int page = 1; page <= cap; page++) {
            var got = BiliBiliSpaceUtil.fetchVideos(mid, page, BiliBiliSpaceUtil.MAX_PAGE_SIZE, "pubdate");
            seen.addAll(bvidsOf(got.videos()));
            var fresh = pickNew(fromSpace(got.videos()), known);
            out.addAll(fresh);
            if (fresh.isEmpty() && seen.containsAll(wantPubdates))
                break;
            if (got.videos().size() < BiliBiliSpaceUtil.MAX_PAGE_SIZE)
                break;
            if (delay > 0)
                Thread.sleep(delay);
        }
        LinkDebug.log("playlist refresh scope", "mid", mid, "kind", "all", "new", out.size());
        return out;
    }

    /**
     * Scope {@code season}: only that 合集's members; the cached name's {@code 合集·} label is stripped on both sides before comparing.
     */
    private static List<NewVideo> walkSeason(long mid, Set<String> known, String season, int maxPages, int delay) throws Exception {
        if (season == null || season.isBlank())
            throw new IllegalStateException("no 合集 information for this playlist");
        long seasonId = -1L;
        for (var s : BiliBiliSpaceUtil.fetchSeasons(mid)) {
            if (seasonKey(s.name()).equals(seasonKey(season))) {
                seasonId = s.seasonId();
                break;
            }
        }
        if (seasonId < 0)
            throw new IllegalStateException("the playlist's 合集 is not among this UP's 合集: " + season);
        LinkDebug.log("playlist refresh scope", "mid", mid, "kind", "season", "season", season, "seasonId", seasonId);
        var out = new ArrayList<NewVideo>();
        var seen = new LinkedHashSet<String>();
        var cap = maxPages <= 0 ? Integer.MAX_VALUE : maxPages;
        var pages = 0;
        var total = 0;
        for (int page = 1; page <= cap; page++) {
            // ⚠️ **newest first** — with the default order a 合集 answers oldest first, so the early stop below would
            // fire on page 1 and the newest episode would never be seen.
            var got = BiliBiliSpaceUtil.fetchSeasonVideos(mid, seasonId, page, BiliBiliSpaceUtil.MAX_PAGE_SIZE, true);
            pages++;
            total = got.total();
            seen.addAll(bvidsOf(got.videos()));
            var fresh = pickNew(fromSpace(got.videos()), known);
            out.addAll(fresh);
            if (fresh.isEmpty() && seen.containsAll(wantPubdates))
                break;
            if (got.videos().size() < BiliBiliSpaceUtil.MAX_PAGE_SIZE)
                break;
            if (delay > 0)
                Thread.sleep(delay);
        }
        LinkDebug.log("playlist refresh season", "mid", mid, "season", season, "seasonId", seasonId,
                "members", total, "pages", pages, "new", out.size(), "pubdatesSeen", seen.size());
        return out;
    }

    /** 合集 names are compared without the {@code 合集·} label the tag cache may carry. */
    static String seasonKey(String name) {
        var n = name == null ? "" : name.strip();
        return n.startsWith("合集·") ? n.substring(3).strip() : n;
    }

    /**
     * Whether the bound UP really uploaded the songs — compared by **name**; binding off the **first** song's owner can land on a stranger credited with the whole playlist.
     */
    static boolean upNameMatches(String upName, List<Music> existing) {
        var up = upName == null ? "" : upName.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
        if (up.isEmpty())
            return true;                       // nothing to compare against: do not block the walk on a missing name
        var counts = new LinkedHashMap<String, Integer>();
        if (existing != null) {
            for (var m : existing) {
                if (!isBiliBili(m))
                    continue;
                var author = m.getAuthor() == null ? "" : m.getAuthor().replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
                if (!author.isEmpty())
                    counts.merge(author, 1, Integer::sum);
            }
        }
        var dominant = dominant(counts);
        return dominant.isEmpty() || dominant.equals(up);
    }

    /** What the walk is limited to (see {@code IMPConfig.BilibiliConfig#refreshScope}). */
    record Scope(String kind, Set<String> zones, String season) {
    }

    /**
     * Scope for one playlist: the content decides, never the name; {@code auto} = the 合集 when ≥ half the tagged B站 songs sit in one, else the 分区; no data ⇒ throws.
     */
    static Scope resolveScope(dev.felnull.imp.IMPConfig.RefreshScope configured, List<Music> existing) {
        var mode = configured == null ? dev.felnull.imp.IMPConfig.RefreshScope.AUTO : configured;
        var seasons = seasonsOf(existing);
        var zones = zonesOf(existing);
        return switch (mode) {
            case ALL -> new Scope("all", Set.of(), "");
            case SEASON -> new Scope("season", Set.of(), dominant(seasons));
            case ZONE -> new Scope("zone", zones, "");
            default -> {
                var season = dominant(seasons);
                if (!season.isBlank())
                    yield new Scope("season", Set.of(), season);
                yield new Scope("zone", zones, "");
            }
        };
    }

    /** The most frequent entry of a name→count map ({@code ""} when the map is empty). */
    static String dominant(Map<String, Integer> counts) {
        String best = "";
        int bestCount = 0;
        for (var e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }

    /**
     * The playlist's zones, from the local tag cache: a zone counts from a tenth of its tagged B站 songs up, so one stray video cannot widen the scope; nothing cached ⇒ empty set ⇒ refuse.
     */
    public static Set<String> zonesOf(List<Music> existing) {
        var counts = countsOf(existing, true);
        var out = new LinkedHashSet<String>();
        var total = counts.values().stream().mapToInt(Integer::intValue).sum();
        for (var e : counts.entrySet())
            if (total > 0 && e.getValue() * 10 >= total)
                out.add(e.getKey());
        return out;
    }

    /**
     * How many of the playlist's tagged B站 songs sit in each **合集** (key = the name without its {@code 合集·} label); used by {@code refreshScope=season} and {@code auto}.
     */
    public static Map<String, Integer> seasonsOf(List<Music> existing) {
        return countsOf(existing, false);
    }

    /**
     * Tag counts over the playlist's B站 songs: {@code byZone} = parent zone, else 合集 membership (a video in several 合集 counts once per membership).
     */
    private static Map<String, Integer> countsOf(List<Music> existing, boolean byZone) {
        var counts = new LinkedHashMap<String, Integer>();
        if (existing == null)
            return counts;
        for (var m : existing) {
            if (!isBiliBili(m))
                continue;
            var bvid = BiliBiliUtil.stripPage(m.getSource().getIdentifier());
            if (bvid == null || bvid.isBlank())
                continue;
            var tag = BiliBiliArcTags.getInstance().get(bvid);
            if (tag == null)
                continue;
            if (byZone) {
                var zone = tag.zone() == null ? "" : tag.zone().strip();
                if (!zone.isEmpty())
                    counts.merge(zone, 1, Integer::sum);
            } else if (tag.seasons() != null) {
                for (var season : tag.seasons()) {
                    var key = seasonKey(season);
                    if (!key.isEmpty())
                        counts.merge(key, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    /** The UP's current avatar; empty when the request fails (the cover then simply stays as it was). */
    private static String upFace(long mid) {
        try {
            return BiliBiliSpaceUtil.fetchUpInfo(mid).face();
        } catch (Throwable t) {
            return "";
        }
    }

    /** One favourites folder: every bvid in one request, and only the **new** ones cost a per-video request. */
    private static FavResult walkFav(long fid, Set<String> known, int delay) throws Exception {
        var cover = "";
        try {
            cover = BiliBiliUtil.fetchFavInfo(fid).cover();
        } catch (Throwable ignored) {
            // Cosmetic only: the playlist keeps the cover it has
        }
        var out = new ArrayList<NewVideo>();
        for (var bvid : BiliBiliUtil.fetchFavBvids(fid)) {
            if (known.contains(bvid))
                continue;
            var v = BiliBiliUtil.fetchVideo(bvid);
            if (v != null)
                out.add(new NewVideo(bvid, v.title(), v.author(), v.duration() * 1000L, v.cover()));
            if (delay > 0)
                Thread.sleep(delay);
        }
        return new FavResult(out, cover);
    }

    private record FavResult(List<NewVideo> videos, String cover) {
    }

    /**
     * The two packets: the new songs plus the source/cover bookkeeping. Client thread; {@code invalidateMusics} is what makes the added rows appear.
     */
    /**
     * Drops songs the walk found gone ({@code -404} only; one {@code view} per new song) — a listing keeps handing out deleted submissions for a while.
     */
    private static List<NewVideo> keepExisting(UUID playlistId, List<NewVideo> videos) {
        if (videos.isEmpty())
            return videos;
        var out = new ArrayList<NewVideo>(videos.size());
        for (var v : videos) {
            if (BiliBiliParts.fetch(v.bvid()) == null
                    && BiliBiliParts.failureOf(v.bvid()) == BiliBiliParts.Failure.GONE) {
                LinkDebug.log("playlist refresh skip gone", "playlist", playlistId, "bv", v.bvid(), "title", v.title());
                continue;
            }
            out.add(v);
        }
        return out;
    }

    private static void send(UUID playlistId, String source, List<NewVideo> videos, String cover, UUID owner,
                             boolean loadImage, String rename) {
        try {
            var existence = refreshExistence;
            if (existence == null) {
                error = "no block entity";
                state = State.FAILED;
                LinkDebug.log("playlist refresh send SKIPPED", "playlist", playlistId, "why", "existence=null");
                return;
            }
            var musics = new ArrayList<Music>(videos.size());
            for (var v : videos)
                musics.add(toMusic(v, loadImage, owner));
            if (!musics.isEmpty())
                NetworkManager.sendToServer(IMPPackets.MULTIPLE_MUSIC_ADD,
                        new IMPPackets.MultipleMusicAddMessage(playlistId, musics, existence).toRFBB());
            if (!source.isBlank() || !cover.isBlank() || !rename.isBlank())
                NetworkManager.sendToServer(IMPPackets.MUSIC_PLAYLIST_SOURCE,
                        new IMPPackets.PlayListSourceMessage(playlistId, source,
                                cover.isBlank() ? ImageInfo.EMPTY : new ImageInfo(ImageInfo.ImageType.URL, cover),
                                rename, existence).toRFBB());
            // The add is not broadcast by the server: expire both caches so the new rows show up within a tick
            MusicSyncManager.getInstance().invalidateMusics(playlistId);
            MusicSyncManager.getInstance().invalidatePlayLists();
            LinkDebug.log("playlist refresh sent", "playlist", playlistId, "songs", musics.size(),
                    "cover", cover.isBlank() ? "(none)" : "yes", "rename", rename.isBlank() ? "(no)" : rename);
        } catch (Throwable t) {
            error = BiliBiliUtil.fetchFailReason(t);
            state = State.FAILED;
            LinkDebug.log("playlist refresh send FAIL", "playlist", playlistId, "ex", error);
        }
    }

    /**
     * Renames a legacy {@code "<UP 名> 的投稿"} **without** a refresh — a playlist bound by an earlier version would otherwise keep the old name.
     */
    public static void fixLegacyName(UUID playlistId, String playlistName) {
        var rename = legacyNameFixed(playlistName);
        if (playlistId == null || rename.isBlank())
            return;
        var existence = refreshExistence;
        if (existence == null)
            return;
        NetworkManager.sendToServer(IMPPackets.MUSIC_PLAYLIST_SOURCE,
                new IMPPackets.PlayListSourceMessage(playlistId, "", ImageInfo.EMPTY, rename, existence).toRFBB());
        MusicSyncManager.getInstance().invalidatePlayLists();
        LinkDebug.log("playlist name fixed", "playlist", playlistId, "name", playlistName, "to", rename);
    }

    /**
     * Block entity the click came from, set by the page before {@link #start}; the server checks it, so songs cannot be added from anywhere in the world.
     */
    private static volatile BlockEntityExistence refreshExistence;

    public static void setExistence(BlockEntityExistence existence) {
        refreshExistence = existence;
    }

    /**
     * Claim an old playlist: writes **only** the source; UP by evidence — 合集 owner → a candidate's name inside the playlist name → a vote over ≤{@link #CLAIM_SAMPLE} songs.
     */
    public static void claim(UUID playlistId, String playlistName, List<Music> musics, BlockEntityExistence existence) {
        if (playlistId == null || state == State.RUNNING)
            return;
        state = State.RUNNING;
        error = "";
        finishedAt = 0L;
        playlist = playlistId;
        added = 0;
        needsRebind = false;
        setExistence(existence);
        // Legacy "<UP 名> 的投稿" → "<UP 名>的投稿" (the name is stored data, so it must be written back)
        var renamed = legacyNameFixed(playlistName);
        LinkDebug.log("playlist source claim", "playlist", playlistId, "name", playlistName,
                "songs", musics == null ? -1 : musics.size(), "rename", renamed.isBlank() ? "(no)" : renamed);
        var thread = new Thread(() -> {
            try {
                var mid = resolveUpMid(playlistName, musics,
                        Math.max(0, IamMusicPlayer.getConfig().bilibiliConfig.spacePageDelayMs));
                if (mid >= 0) {
                    var source = BiliBiliSpaceUtil.ID_PREFIX + mid;
                    Minecraft.getInstance().execute(() -> {
                        var ex = refreshExistence;
                        if (ex == null)
                            return;
                        NetworkManager.sendToServer(IMPPackets.MUSIC_PLAYLIST_SOURCE,
                                new IMPPackets.PlayListSourceMessage(playlistId, source, ImageInfo.EMPTY, renamed, ex).toRFBB());
                        // ⚠️ The server does not broadcast a playlist change: without dropping the playlist-list cache
                        // the client keeps its old object (source = "") for up to 60 s and 「刷新」 stays hidden.
                        MusicSyncManager.getInstance().invalidatePlayLists();
                        LinkDebug.log("playlist source claimed", "playlist", playlistId, "mid", mid,
                                "songs", musics == null ? -1 : musics.size(),
                                "renamed", renamed.isBlank() ? "(no)" : renamed);
                    });
                    state = State.DONE;
                    finishedAt = System.currentTimeMillis();
                    return;
                }
                error = "";
                state = State.FAILED;
                finishedAt = System.currentTimeMillis();
                LinkDebug.log("playlist source claim FAIL", "playlist", playlistId,
                        "why", "no UP could be decided from the songs",
                        "songs", musics == null ? -1 : musics.size());
            } catch (Throwable t) {
                error = BiliBiliUtil.fetchFailReason(t);
                state = State.FAILED;
                finishedAt = System.currentTimeMillis();
                LinkDebug.log("playlist source claim FAIL", "playlist", playlistId, "ex", error);
            }
        }, "IMP-BiliBili-Source-Claim");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * The legacy import name with the space dropped ({@code "某个UP主 的投稿"} → {@code "某个UP主的投稿"}); {@code ""} = nothing to fix, name untouched.
     */
    public static String legacyNameFixed(String name) {
        var n = name == null ? "" : name.strip();
        if (!n.endsWith(" 的投稿") || n.length() <= 4)
            return "";
        return n.substring(0, n.length() - 4) + "的投稿";
    }

    /**
     * The UP a playlist belongs to, by evidence: 合集 owner → its name inside the playlist name → a sampled majority (**over half**, since a 39 % share can win a small vote).
     *
     * @return the mid to bind, or {@code -1} when nothing is convincing enough
     */
    static long resolveUpMid(String playlistName, List<Music> musics, int delayMs) throws Exception {
        var bvids = new ArrayList<String>();
        if (musics != null) {
            for (var m : musics) {
                if (!isBiliBili(m))
                    continue;
                var bvid = BiliBiliUtil.stripPage(m.getSource().getIdentifier());
                if (bvid != null && !bvid.isBlank())
                    bvids.add(bvid);
            }
        }
        if (bvids.isEmpty())
            return -1L;
        // ---- candidates + vote counts, from all roles of the sampled videos ----
        var votes = new LinkedHashMap<Long, Integer>();
        var usable = 0;
        for (var index : sampleIndexes(bvids.size())) {
            var mids = BiliBiliUtil.fetchCreditedMids(bvids.get(index));
            if (mids.isEmpty())
                continue;
            usable++;
            for (var mid : mids)
                votes.merge(mid, 1, Integer::sum);
            if (delayMs > 0)
                Thread.sleep(delayMs);
        }
        if (votes.isEmpty())
            return -1L;
        // Strongest first, so the name check below also prefers the most credited candidate
        var candidates = new ArrayList<>(votes.keySet());
        candidates.sort((a, b) -> Integer.compare(votes.get(b), votes.get(a)));
        if (candidates.size() == 1)
            return candidates.get(0);
        // ---- 1) who owns the playlist's 合集 ----
        var season = dominant(seasonsOf(musics));
        if (!season.isBlank()) {
            for (var mid : candidates) {
                try {
                    for (var s : BiliBiliSpaceUtil.fetchSeasons(mid)) {
                        if (seasonKey(s.name()).equals(season)) {
                            LinkDebug.log("playlist bind by season", "season", season, "mid", mid,
                                    "candidates", candidates.toString());
                            return mid;
                        }
                    }
                } catch (Throwable t) {
                    LinkDebug.log("playlist bind season probe FAIL", "mid", mid, "ex", t.toString());
                }
            }
        }
        // ---- 2) whose name is in the playlist name ----
        var name = playlistName == null ? "" : playlistName.replaceAll("\\s+", "");
        for (var mid : candidates) {
            try {
                var upName = BiliBiliSpaceUtil.fetchUpInfo(mid).name().replaceAll("\\s+", "");
                if (!upName.isEmpty() && name.contains(upName)) {
                    LinkDebug.log("playlist bind by name", "playlistName", playlistName, "upName", upName, "mid", mid);
                    return mid;
                }
            } catch (Throwable t) {
                LinkDebug.log("playlist bind name probe FAIL", "mid", mid, "ex", t.toString());
            }
        }
        // ---- 3) the vote, and only when it is a real majority ----
        var best = candidates.get(0);
        LinkDebug.log("playlist bind by vote", "mid", best, "votes", votes.get(best), "samples", usable,
                "allVotes", votes.toString());
        return votes.get(best) * 2 > usable ? best : -1L;
    }

    /** How many songs a bind samples; the vote must be a real majority of them. */
    public static final int CLAIM_SAMPLE = 5;

    /** Up to {@link #CLAIM_SAMPLE} indexes spread over the whole list (first, last and evenly spaced ones between). */
    static List<Integer> sampleIndexes(int size) {
        var out = new ArrayList<Integer>(CLAIM_SAMPLE);
        if (size <= 0)
            return out;
        if (size <= CLAIM_SAMPLE) {
            for (int i = 0; i < size; i++)
                out.add(i);
            return out;
        }
        for (int i = 0; i < CLAIM_SAMPLE; i++)
            out.add((int) ((long) i * (size - 1) / (CLAIM_SAMPLE - 1)));
        return out;
    }
}
