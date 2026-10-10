package dev.felnull.imp.client.bilibili;

import dev.felnull.imp.client.music.link.LinkDebug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads a UP's space into the view model: submissions plus the two groupings (the UP's seasons, Bilibili's parent
 * zone). Own thread, UI polls the volatile snapshot; paced by {@code delayMs} and capped by {@code maxPages} because risk
 * control answers 412 / {@code -352} to a burst. Plan: page 1 ({@code tlist}) → per zone ({@code tid}) → seasons
 * ({@code seasons_archives_list}).
 */
public final class BiliBiliSpaceModel {
    /** Overall load state (the view only draws rows once it is {@link #DONE}). */
    public enum State {
        IDLE, LOADING, DONE, FAILED
    }

    /** Loading stage, shown while the model is {@link State#LOADING}. */
    public enum Stage {
        IDLE, VIDEOS, ZONES, SEASONS, DONE, FAILED
    }

    /** One row of the category list: either a season (UP-defined) or a parent zone. */
    public record Category(String name, int count, boolean zone) {
    }

    /** Everything known about one video beyond the submission fields themselves. */
    public record Tag(String zone, Set<String> seasons) {
        public static final Tag EMPTY = new Tag("", Set.of());

        /** Same seasons, new parent zone (used while walking the per-zone passes). */
        public Tag withZone(String zone) {
            return new Tag(zone, seasons);
        }

        /** Adds one season membership; the set is copied so a published tag is never mutated in place. */
        public Tag withSeason(String season) {
            var next = new LinkedHashSet<>(seasons);
            next.add(season);
            return new Tag(zone, Set.copyOf(next));
        }
    }

    /** Tick key of the "no season" row; the SEASON/ZONE keys are built by {@link #tickKey}. */
    public static final String TICK_UNGROUPED = "U";

    /** Tick key of one category row ({@code S:<season name>} / {@code Z:<parent zone name>}). */
    public static String tickKey(boolean zone, String name) {
        return (zone ? "Z:" : "S:") + name;
    }

    /** The UP being loaded; {@code -1} until the first {@link #start(long)}. */
    private volatile long mid = -1;
    /**
     * Load generation: every {@link #start(long)} bumps it and the new thread captures its own value, so a load still
     * running after a restart stops on a stale token (a plain {@code cancelled} flag would let it carry on).
     */
    private volatile long epoch;
    /** Worker pool of the running load (daemon threads); replaced on every start and shut down by {@link #reset()}. */
    private volatile java.util.concurrent.ExecutorService pool;
    private final BiliBiliPacer pacer = new BiliBiliPacer();

    private volatile State state = State.IDLE;
    private volatile Stage stage = Stage.IDLE;
    private volatile String error = "";
    private volatile String upName = "";
    private volatile String cover = "";
    private volatile int total;
    private volatile int loaded;
    private volatile List<BiliBiliSpaceUtil.SpaceVideo> videos = List.of();
    private volatile Map<String, Tag> tags = Map.of();
    private volatile List<BiliBiliSpaceUtil.Season> seasonList = List.of();
    private volatile List<BiliBiliSpaceUtil.ZoneIndex> zoneList = List.of();
    private volatile Set<String> selected = new LinkedHashSet<>();

    /**
     * True when the video list came from the fallback endpoint: the parent zone and the charging-only / paid course /
     * live replay flags are then unknown, so there is no zone dimension and those entries are not excluded.
     */
    private volatile boolean filterRelaxed;

    /**
     * Ticked category rows (see {@link #tickKey}); empty = everything. Their union is both what the list shows and
     * what "Import ticked categories" takes, so one import can cover several seasons.
     */
    private volatile Set<String> categoryTicks = new LinkedHashSet<>();

    /** Avatar URL of the UP (from {@code x/web-interface/card}); empty when unavailable. */
    private volatile String face = "";

    public long mid() {
        return mid;
    }

    /** True when this model already holds a finished load of exactly this UP. */
    public boolean isLoadedFor(long mid) {
        return this.mid == mid && state == State.DONE;
    }

    public State state() {
        return state;
    }

    public Stage stage() {
        return stage;
    }

    public String error() {
        return error;
    }

    public String upName() {
        return upName;
    }

    public String cover() {
        return cover;
    }

    /** URL of the UP's avatar ({@code i*.hdslb.com}), or {@code ""} when it could not be fetched. */
    public String face() {
        return face;
    }

    public int total() {
        return total;
    }

    /** See {@link #filterRelaxed} — the view says so instead of pretending the filter ran. */
    public boolean filterRelaxed() {
        return filterRelaxed;
    }

    /** Videos collected so far (grows while loading). */
    public int loaded() {
        return loaded;
    }

    public List<BiliBiliSpaceUtil.SpaceVideo> videos() {
        return videos;
    }

    public Tag tagOf(String bvid) {
        return tags.getOrDefault(bvid, Tag.EMPTY);
    }

    /** Name of the playlist the import builds ("&lt;UP name&gt;的投稿" — no space: the user asked for the space to be removed). */
    public String playlistName() {
        return upName.isBlank() ? ("UP " + mid + "的投稿") : (upName + "的投稿");
    }

    /** The season rows, biggest first; the counts come from the tags, so a row lists exactly as many videos as it says. */
    public List<Category> seasons() {
        var out = new ArrayList<Category>();
        var seen = new LinkedHashSet<String>();
        for (var s : seasonList) {
            seen.add(s.name());
            out.add(new Category(s.name(), countTagged(t -> t.seasons().contains(s.name())), false));
        }
        var extras = new LinkedHashMap<String, Integer>();
        for (var v : videos) {
            for (var name : tagOf(v.bvid()).seasons()) {
                if (!seen.contains(name))
                    extras.merge(name, 1, Integer::sum);
            }
        }
        for (var e : extras.entrySet())
            out.add(new Category(e.getKey(), e.getValue(), false));
        out.sort((a, b) -> Integer.compare(b.count(), a.count()));
        return out;
    }

    /** The parent-zone rows (API order, i.e. biggest first), counted from the tags like {@link #seasons()}. */
    public List<Category> zones() {
        var counts = new LinkedHashMap<String, Integer>();
        for (var z : zoneList)
            counts.put(z.name(), 0);
        for (var v : videos) {
            var name = tagOf(v.bvid()).zone();
            if (!name.isBlank())
                counts.merge(name, 1, Integer::sum);
        }
        var out = new ArrayList<Category>();
        for (var e : counts.entrySet())
            out.add(new Category(e.getKey(), e.getValue(), true));
        return out;
    }

    /** How many loaded videos belong to no season at all. */
    public int ungroupedCount() {
        return countTagged(t -> t.seasons().isEmpty());
    }

    private int countTagged(java.util.function.Predicate<Tag> match) {
        var count = 0;
        for (var v : videos) {
            if (match.test(tagOf(v.bvid())))
                count++;
        }
        return count;
    }

    // ------------------------------------------------------------------ category ticks

    /** Ticked category keys, as an immutable snapshot (the UI iterates it every frame). */
    public Set<String> categoryTicks() {
        return Set.copyOf(categoryTicks);
    }

    public boolean isCategoryTicked(String key) {
        return categoryTicks.contains(key);
    }

    /** Ticks/untick one category row (see {@link #tickKey}); "ungrouped" uses {@link #TICK_UNGROUPED}. */
    public void toggleCategoryTick(String key) {
        if (key == null || key.isBlank())
            return;
        var next = new LinkedHashSet<>(categoryTicks);
        if (!next.remove(key))
            next.add(key);
        categoryTicks = next;
    }

    /** The "All" row: no ticks at all means "the whole list". */
    public void clearCategoryTicks() {
        categoryTicks = new LinkedHashSet<>();
    }

    /** True when one video belongs to any of the ticked categories. */
    private boolean matchesTicks(BiliBiliSpaceUtil.SpaceVideo video) {
        var tag = tags.getOrDefault(video.bvid(), Tag.EMPTY);
        for (var key : categoryTicks) {
            if (TICK_UNGROUPED.equals(key)) {
                if (tag.seasons().isEmpty())
                    return true;
            } else if (key.startsWith("S:")) {
                if (tag.seasons().contains(key.substring(2)))
                    return true;
            } else if (key.startsWith("Z:")) {
                if (key.substring(2).equals(tag.zone()))
                    return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ selection

    /** Selected ids, as an immutable snapshot (the UI iterates it every frame). */
    public Set<String> selected() {
        return Set.copyOf(selected);
    }

    public int selectedCount() {
        return selected.size();
    }

    public boolean isSelected(String bvid) {
        return selected.contains(bvid);
    }

    public void toggleSelected(String bvid) {
        var next = new LinkedHashSet<>(selected);
        if (!next.remove(bvid))
            next.add(bvid);
        selected = next;
    }

    /** Select exactly the given ids (used by "select all" on the currently visible rows). */
    public void setSelected(Set<String> ids) {
        selected = new LinkedHashSet<>(ids);
    }

    public void clearSelected() {
        selected = new LinkedHashSet<>();
    }

    // ------------------------------------------------------------------ filtering

    /**
     * The videos the view should show now: the whole list, or only the rows matching the category filter. The selection
     * is deliberately not trimmed (importing only adds), so the view shows "N selected (M filtered out)" for hidden ticks.
     */
    public List<BiliBiliSpaceUtil.SpaceVideo> visibleVideos() {
        var all = videos;
        if (categoryTicks.isEmpty())
            return all;
        var out = new ArrayList<BiliBiliSpaceUtil.SpaceVideo>(all.size());
        for (var v : all) {
            if (matchesTicks(v))
                out.add(v);
        }
        return out;
    }

    /** How many videos the ticked categories hold **together** (the deduplicated union) — the import button's count. */
    public int tickedVideosCount() {
        if (categoryTicks.isEmpty())
            return videos.size();
        return visibleVideos().size();
    }

    /**
     * The ticked videos in submission order — "Import ticked only" imports exactly this, including ticks the current filter
     * hides (the view warns about that count, it never silently drops them).
     */
    public List<BiliBiliSpaceUtil.SpaceVideo> selectedVideos() {
        var out = new ArrayList<BiliBiliSpaceUtil.SpaceVideo>();
        for (var v : videos) {
            if (selected.contains(v.bvid()))
                out.add(v);
        }
        return out;
    }

    /** How many of the selected videos the current category ticks hide. */
    public int selectedHiddenByFilter() {
        if (categoryTicks.isEmpty())
            return 0;
        var visible = new LinkedHashSet<String>();
        for (var v : visibleVideos())
            visible.add(v.bvid());
        var hidden = 0;
        for (var id : selected) {
            if (!visible.contains(id))
                hidden++;
        }
        return hidden;
    }

    /** Charging-only / paid course / live replay entries do not resolve to playable audio, so they never enter the model. */
    private static boolean skip(BiliBiliSpaceUtil.SpaceVideo v) {
        return v.chargingOnly() || v.lesson() || v.livePlayback();
    }

    // ------------------------------------------------------------------ loading

    /**
     * Starts the background load; the same UP while loading is a no-op, so the view can call it every tick.
     *
     * @param maxPages request cap for the whole load, {@code <= 0} = no cap (the view passes {@code spaceMaxPages})
     * @param delayMs  pause between request starts (Bilibili's risk control answers 412 to a burst); with
     *                 {@code parallelism > 1} the rate is unchanged but the latencies overlap
     * @param parallelism number of concurrent workers ({@code 1} = the old strictly sequential behaviour)
     */
    public void start(long mid, int maxPages, int delayMs, int parallelism) {
        if (this.mid == mid && (state == State.LOADING || state == State.DONE))
            return;

        this.mid = mid;
        var token = ++epoch;
        var oldPool = pool;
        if (oldPool != null)
            oldPool.shutdownNow();
        pool = poolFor(parallelism);
        state = State.LOADING;
        stage = Stage.VIDEOS;
        error = "";
        videos = List.of();
        tags = Map.of();
        seasonList = List.of();
        zoneList = List.of();
        loaded = 0;
        total = 0;
        upName = "";
        cover = "";
        face = "";
        filterRelaxed = false;
        selected = new LinkedHashSet<>();
        categoryTicks = new LinkedHashSet<>();

        var thread = new Thread(() -> loadAll(token, maxPages, Math.max(0, delayMs)), "BilibiliSpaceModel");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Stops a running load and forgets everything; the generation is bumped first, so an in-flight load stops at its
     * next check instead of writing into the model.
     */
    public void reset() {
        epoch++;
        // Stop the workers right away: their pending sleep would otherwise keep asking Bilibili for a page nobody
        // is looking at any more
        var running = pool;
        if (running != null)
            running.shutdownNow();
        pool = null;
        mid = -1;
        state = State.IDLE;
        stage = Stage.IDLE;
        error = "";
        upName = "";
        cover = "";
        face = "";
        total = 0;
        loaded = 0;
        videos = List.of();
        tags = Map.of();
        seasonList = List.of();
        zoneList = List.of();
        selected = new LinkedHashSet<>();
        categoryTicks = new LinkedHashSet<>();
        filterRelaxed = false;
    }

    private boolean stale(long token) {
        return epoch != token;
    }

    private void loadAll(long token, int maxPageCount, int delay) {
        try {
            var maxPages = maxPageCount <= 0 ? Integer.MAX_VALUE : maxPageCount;
            var pagesLeft = new java.util.concurrent.atomic.AtomicInteger(maxPages);

            // Several workers write here at once: every mutation and every snapshot is taken under the map's own lock.
            var collected = java.util.Collections.synchronizedMap(new LinkedHashMap<String, BiliBiliSpaceUtil.SpaceVideo>());
            var newTags = java.util.Collections.synchronizedMap(new LinkedHashMap<String, Tag>());

            // ---- 1) first page: total, UP name, cover, zone index ----
            // The only source of the zone index and per-video flags, but risk control refuses it (-352 / HTTP 412);
            // on refusal we fall back to "all videos in one request" and report filterRelaxed() (no zone).
            BiliBiliSpaceUtil.VideoPage first = null;
            var relaxed = false;
            try {
                first = BiliBiliSpaceUtil.fetchVideos(mid, 1, BiliBiliSpaceUtil.MAX_PAGE_SIZE, "pubdate");
            } catch (BiliBiliSpaceUtil.SpaceApiException e) {
                if (!e.isRiskControl())
                    throw e;
                LinkDebug.log("BiliBili space arc/search refused, falling back", "mid", mid, "reason", e.getMessage());
                relaxed = true;
            }
            if (stale(token))
                return;

            if (first != null) {
                total = first.total();
                zoneList = first.zones();
                for (var v : first.videos()) {
                    if (!skip(v))
                        collected.put(v.bvid(), v);
                    if (upName.isBlank() && !v.author().isBlank())
                        upName = v.author();
                    if (cover.isBlank() && !v.cover().isBlank())
                        cover = v.cover();
                }
            } else {
                var all = BiliBiliSpaceUtil.fetchAllByKeywords(mid);
                for (var v : all)
                    collected.put(v.bvid(), v);
                total = collected.size();
                if (!collected.isEmpty())
                    cover = collected.values().iterator().next().cover();
            }
            filterRelaxed = relaxed;
            loaded = collected.size();
            videos = List.copyOf(collected.values());

            // ---- 1b) the UP's name + avatar (one request; the card endpoint is the one profile call that also answers
            // anonymously while arc/search is refused, so the avatar does not depend on being logged in) ----
            try {
                var up = BiliBiliSpaceUtil.fetchUpInfo(mid);
                if (!up.name().isBlank())
                    upName = up.name();
                if (!up.face().isBlank())
                    face = up.face();
            } catch (Exception e) {
                // Cosmetic only: the placeholder box / "UP <mid>" name cover for it
                LinkDebug.log("BiliBili space up info FAIL", "mid", mid, "ex", e.getMessage());
            }
            if (stale(token))
                return;

            // ---- 2) one pass per zone (the only way to learn each video's parent zone) ----
            // Skipped whole when the zone index did not arrive (the fallback has none). Zones run in parallel:
            // each request still waits for its pacer slot, so the rate is unchanged while latencies overlap.
            stage = Stage.ZONES;
            if (!zoneList.isEmpty() && !stale(token)) {
                var futures = new ArrayList<java.util.concurrent.Future<?>>();
                for (var zone : zoneList) {
                    futures.add(submit(pool, () -> {
                        for (var page = 1; !stale(token) && pagesLeft.get() > 0; page++) {
                            if (pagesLeft.getAndDecrement() <= 0)
                                break;
                            BiliBiliPacer.await(pacer.reserve(delay));
                            if (stale(token))
                                return;
                            var result = BiliBiliSpaceUtil.fetchVideos(mid, page, BiliBiliSpaceUtil.MAX_PAGE_SIZE, "pubdate", zone.tid());
                            synchronized (collected) {
                                for (var v : result.videos()) {
                                    if (skip(v))
                                        continue;
                                    collected.putIfAbsent(v.bvid(), v);
                                    var old = newTags.getOrDefault(v.bvid(), Tag.EMPTY);
                                    newTags.put(v.bvid(), old.withZone(zone.name()));
                                }
                                publish(collected, newTags);
                            }
                            if (result.videos().isEmpty() || page * BiliBiliSpaceUtil.MAX_PAGE_SIZE >= result.total())
                                break;
                        }
                    }, "zone " + zone.name()));
                }
                joinAll(futures);
            }

            // ---- 3) seasons (UP-defined grouping; membership only, never adds videos) ----
            stage = Stage.SEASONS;
            List<BiliBiliSpaceUtil.Season> seasons;
            try {
                seasons = BiliBiliSpaceUtil.fetchSeasons(mid);
            } catch (Exception e) {
                // The season dimension is a bonus: its failure must not lose the video list we already have
                LinkDebug.log("BiliBili space seasons FAIL", "mid", mid, "ex", e.getMessage());
                seasons = List.of();
            }
            if (!stale(token)) {
                seasonList = seasons;
                // The member passes are the bulk of the load. They also run in parallel, and this endpoint is not behind the
                // risk control that guards the submission API (it answers without WBI and without cookies), so it is paced looser.
                var memberGap = Math.max(60, delay / 4);
                if (!seasons.isEmpty()) {
                    var futures = new ArrayList<java.util.concurrent.Future<?>>();
                    for (var s : seasons) {
                        futures.add(submit(pool, () -> {
                            for (var page = 1; !stale(token); page++) {
                                if (pagesLeft.getAndDecrement() <= 0)
                                    break;
                                BiliBiliPacer.await(pacer.reserve(memberGap));
                                if (stale(token))
                                    return;
                                BiliBiliSpaceUtil.VideoPage result;
                                try {
                                    result = BiliBiliSpaceUtil.fetchSeasonVideos(mid, s.seasonId(), page, BiliBiliSpaceUtil.MAX_PAGE_SIZE);
                                } catch (Exception e) {
                                    LinkDebug.log("BiliBili space season FAIL", "mid", mid, "season", s.seasonId(), "ex", e.getMessage());
                                    return;
                                }
                                synchronized (collected) {
                                    for (var v : result.videos()) {
                                        var old = newTags.getOrDefault(v.bvid(), Tag.EMPTY);
                                        newTags.put(v.bvid(), old.withSeason(s.name()));
                                    }
                                    tags = Map.copyOf(newTags);
                                }
                                if (result.videos().isEmpty() || page * BiliBiliSpaceUtil.MAX_PAGE_SIZE >= result.total())
                                    break;
                            }
                        }, "season " + s.name()));
                    }
                    joinAll(futures);
                }
            }

            if (stale(token))
                return;
            state = State.DONE;
            stage = Stage.DONE;
            LinkDebug.log("BiliBili space model done", "mid", mid, "videos", videos.size(), "total", total,
                    "zones", zoneList.size(), "seasons", seasonList.size());
        } catch (Exception e) {
            if (!stale(token))
                fail(e.getMessage() == null ? e.toString() : e.getMessage());
        } catch (Throwable t) {
            // Catch Throwable, not just Exception: a missing class / failed static initialiser inside the request path
            // would otherwise kill this thread silently and leave the view spinning on "loading" forever.
            if (!stale(token))
                fail(t.toString());
        }
    }

    private void fail(String message) {
        error = message;
        state = State.FAILED;
        stage = Stage.FAILED;
        LinkDebug.log("BiliBili space model FAIL", "mid", mid, "reason", message);
    }

    // ------------------------------------------------------------------ parallel workers

    /** A worker body that is allowed to throw (the request helpers declare {@code Exception}). */
    @FunctionalInterface
    private interface Worker {
        void run() throws Exception;
    }

    /**
     * Runs one worker task on the shared pool; a failing zone / season is logged and dropped, never fatal — the video
     * list and the other categories are still worth showing.
     */
    private java.util.concurrent.Future<?> submit(java.util.concurrent.ExecutorService pool, Worker work, String what) {
        return pool.submit(() -> {
            try {
                work.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LinkDebug.log("BiliBili space worker FAIL", "what", what, "ex", e.getMessage());
            } catch (Throwable t) {
                LinkDebug.log("BiliBili space worker FAIL", "what", what, "ex", t.toString());
            }
        });
    }

    /** Waits for a phase to finish (its failures were already logged by the worker). */
    private static void joinAll(List<java.util.concurrent.Future<?>> futures) {
        for (var f : futures) {
            try {
                f.get();
            } catch (Exception ignored) {
                // nothing to do here: "the phase is over" is all the caller needs
            }
        }
    }

    /** Publishes the collected set into the volatile snapshot the UI polls (the caller holds the map lock). */
    private void publish(Map<String, BiliBiliSpaceUtil.SpaceVideo> collected, Map<String, Tag> newTags) {
        loaded = collected.size();
        videos = List.copyOf(collected.values());
        tags = Map.copyOf(newTags);
    }

    private static java.util.concurrent.ExecutorService poolFor(int parallelism) {
        return java.util.concurrent.Executors.newFixedThreadPool(Math.max(1, Math.min(8, parallelism)), r -> {
            var t = new Thread(r, "BilibiliSpaceModel-worker");
            t.setDaemon(true);
            return t;
        });
    }

    /** Snapshot for the view: the videos to draw, already filtered and never null. */
    public List<BiliBiliSpaceUtil.SpaceVideo> snapshot() {
        var v = visibleVideos();
        return v == null ? Collections.emptyList() : v;
    }
}
