package dev.felnull.imp.client.bilibili;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.client.music.player.MusicSkipProvider;
import dev.felnull.imp.music.resource.MusicSource;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SponsorBlock's Bilibili mirror ({@code bsbsb.top}) segment table, plus the content-time ↔ source-time conversion.
 * **Any failure counts as "no segments"** and playback goes on. The protocol
 * {@code /api/skipSegments?videoID=<bvid>&categories=[…]} needs no auth header; "no data" is {@code 200 []} and the
 * segment unit is **seconds**.
 */
public final class BiliBiliSponsorSegments {
    public static final String FILE_NAME = "bilibili_sponsor_segments.json";
    /** Only "sponsor" and "selfpromo" are skipped; interaction reminders and the rest are left in place. */
    private static final String CATEGORIES = "[\"sponsor\",\"selfpromo\"]";
    private static final String ENDPOINT = "https://bsbsb.top/api/skipSegments?videoID=";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    /** Crowd-sourced data changes; ask again after a week (a stale entry only affects "skip or not", never playback). */
    private static final long TTL_MS = 7L * 24 * 60 * 60 * 1000;
    /** Cache-file cap; past it the oldest lookups are dropped (a video costs a few dozen bytes). */
    private static final int MAX_VIDEOS = 5000;

    /** One span in source-time milliseconds; {@code cid} says which part it belongs to. */
    public record Segment(long startMs, long endMs, String cid) {
        public long lengthMs() {
            return Math.max(0L, endMs - startMs);
        }
    }

    /** What one video lookup returned: the lookup time, part → cid (noted while playing) and every segment, all parts included. */
    private record Video(long checkedAt, Map<Integer, String> cids, List<Segment> segments) {
    }

    private static final BiliBiliSponsorSegments INSTANCE = new BiliBiliSponsorSegments();
    private final Map<String, Video> videos = new LinkedHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private boolean loaded;
    private boolean dirty;
    private boolean writing;
    private static final ExecutorService QUERY = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "IMP-BiliBili-Sponsor");
        t.setDaemon(true);
        return t;
    });

    private BiliBiliSponsorSegments() {
    }

    public static BiliBiliSponsorSegments getInstance() {
        return INSTANCE;
    }

    /** The switch (off by default); an unreadable config counts as off. */
    public static boolean isEnabled() {
        try {
            return IamMusicPlayer.getConfig().bilibiliConfig.skipSponsorSegments;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The skip table for a player; {@link MusicSkipProvider#NONE} when it does not apply (not Bilibili, a live stream,
     * or the switch is off). The boombox path comes through here too: the server's timeline also runs on content time,
     * which is what keeps both sides on the same position.
     */
    public static MusicSkipProvider providerFor(MusicSource source) {
        if (source == null || source.isLive() || !IMPMusicMedias.BILIBILI.getName().equals(source.getLoaderType()))
            return MusicSkipProvider.NONE;
        if (!isEnabled()) {
            // The server keeps what it was told once; turning the switch off mid-session would otherwise leave it
            // jumping the boombox while this client does not (both ends drift by Σskipped). Say "no segments" once
            // per session — an empty list is the documented "forget this track" (see IMPPackets#BILIBILI_SPONSOR).
            report(source.getIdentifier(), List.of(), true);
            return MusicSkipProvider.NONE;
        }
        return new Provider(source.getIdentifier(), source.getDuration());
    }

    /** One track's skip table, cached once the segments arrive ({@code sourcePositionOf} is asked every frame). */
    private static final class Provider implements MusicSkipProvider {
        private final String identifier;
        private final long durationMs;
        /** {@code null} = no result yet (a lookup may be running); an empty list = looked up, this video has no segments. */
        private volatile List<Segment> resolved;
        /** Read head of the audio reader (source ms), reported by {@link #skips(long)}; 0 = nothing read yet. */
        private volatile long readHeadMs;

        private Provider(String identifier, long durationMs) {
            this.identifier = identifier;
            this.durationMs = durationMs;
        }

        /** The segment table (a snapshot). {@code mayBlock} is only true when playback starts mid-track, where a wrong position would play the wrong place. */
        private List<Segment> segments(boolean mayBlock) {
            var now = resolved;
            if (now != null)
                return now;
            // Starting at 0 needs no wait: a late answer only means the first few seconds of an ad are played.
            ensure(identifier, durationMs, mayBlock);
            var segments = clipToReadHead(segmentsFor(identifier, durationMs));
            if (!segments.isEmpty() || isKnown(identifier)) {
                resolved = segments;
                reportIfNeeded(identifier, segments);
            }
            return segments;
        }

        /**
         * Whatever the read head already passed when the answer arrived counts as "played as is": clamp the segment
         * start to the read head and drop a segment that is entirely behind it. Otherwise that unskipped part would be
         * counted as skipped, shifting the reported position for good and really cutting the tail off. Runs once.
         */
        private List<Segment> clipToReadHead(List<Segment> segments) {
            long head = readHeadMs;
            if (head <= 0 || segments.isEmpty())
                return segments;
            var out = new ArrayList<Segment>(segments.size());
            for (var seg : segments) {
                if (seg.lengthMs() <= 0 || seg.endMs() > head) {
                    out.add(seg.startMs() < head ? new Segment(head, seg.endMs(), seg.cid()) : seg);
                    continue;
                }
                LinkDebug.log("BiliBili sponsor missed", "id", LinkDebug.safeIdentifier(identifier),
                        "span", seg.startMs() + "-" + seg.endMs(), "readHead", head);
            }
            return out;
        }

        @Override
        public List<Span> skips() {
            return spans(segments(false));
        }

        @Override
        public List<Span> skips(long readHeadMs) {
            // Remember the read head: if the table arrives mid-read, the clamp above knows where to start from.
            this.readHeadMs = Math.max(this.readHeadMs, readHeadMs);
            return spans(segments(false));
        }

        @Override
        public long sourcePositionOf(long contentMs) {
            return contentToSource(contentMs, segments(false));
        }

        @Override
        public long contentPositionOf(long sourceMs, boolean mayBlock) {
            return sourceToContent(sourceMs, segments(mayBlock && sourceMs > 0));
        }

        @Override
        public long sourceReadStart(long sourceMs, boolean mayBlock) {
            return readStartFor(sourceMs, segments(mayBlock && sourceMs > 0));
        }
    }

    /**
     * Reports the table to the server once (the boombox timeline finishes on it: content length = source length − the
     * skipped sum). Once per identifier per session; a failed send clears the mark so the next play tries again.
     */
    private static void reportIfNeeded(String identifier, List<Segment> segments) {
        report(identifier, segments, false);
    }

    /**
     * @param allowEmpty send an empty table too (the server forgets the entry, so it stops skipping for this track); used when the switch is turned off
     */
    private static void report(String identifier, List<Segment> segments, boolean allowEmpty) {
        if (identifier == null || identifier.isBlank() || (segments.isEmpty() && !allowEmpty))
            return;
        if (allowEmpty) {
            // This path must bypass the "once per session" gate: reaching it means this session did report (the server
            // may still hold the entry), and being stopped by that gate is exactly why "switch off, server still skips"
            // happened. Clearing the mark also lets a later re-enable report again.
            if (!REPORTED.remove(identifier))
                return;
        } else if (!REPORTED.add(identifier)) {
            return;
        }
        try {
            var starts = new ArrayList<Long>(segments.size());
            var ends = new ArrayList<Long>(segments.size());
            for (var seg : segments) {
                starts.add(seg.startMs());
                ends.add(seg.endMs());
            }
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                try {
                    dev.architectury.networking.NetworkManager.sendToServer(
                            dev.felnull.imp.networking.IMPPackets.BILIBILI_SPONSOR,
                            new dev.felnull.imp.networking.IMPPackets.BiliBiliSponsorMessage(identifier, starts, ends).toRFBB());
                    LinkDebug.log("BiliBili sponsor report", "id", LinkDebug.safeIdentifier(identifier),
                            "segs", segments.size(), "to", "server");
                } catch (Throwable t) {
                    REPORTED.remove(identifier);
                }
            });
        } catch (Throwable t) {
            REPORTED.remove(identifier);
        }
    }

    /** Identifiers already reported to the server in this session. */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    /** Remembers which part's cid this track is (noted during loading, see {@code BiliBiliUtil.fetchAudioCandidates}). */
    public static void rememberCid(String identifier, int page, long cid) {
        var bvid = BiliBiliUtil.stripPage(identifier);
        if (bvid == null || bvid.isBlank() || cid <= 0)
            return;
        var inst = INSTANCE;
        synchronized (inst) {
            var old = inst.videos.get(bvid);
            var cids = new LinkedHashMap<Integer, String>();
            if (old != null)
                cids.putAll(old.cids());
            if (cids.put(Math.max(1, page), String.valueOf(cid)) == null)
                inst.dirty = true;
        }
    }

    /** Looks the table up once when needed and returns what is known; {@code block} allows waiting for the request on the calling thread. */
    public static void ensure(String identifier, long durationMs, boolean block) {
        var bvid = BiliBiliUtil.stripPage(identifier);
        if (bvid == null || bvid.isBlank() || !isEnabled())
            return;
        var inst = INSTANCE;
        var cached = inst.fresh(bvid);
        if (cached != null) {
            if (block)
                return;
            return;
        }
        if (!inst.inFlight.add(bvid))
            return;
        try {
            if (block)
                inst.fetch(bvid);
            else
                QUERY.execute(() -> inst.fetch(bvid));
        } catch (Throwable t) {
            inst.inFlight.remove(bvid);
        }
    }

    /** The cached segments (no network), already picked for the part being played. */
    public static List<Segment> segmentsFor(String identifier, long durationMs) {
        var bvid = BiliBiliUtil.stripPage(identifier);
        if (bvid == null || bvid.isBlank())
            return List.of();
        var inst = INSTANCE;
        Video v;
        synchronized (inst) {
            inst.loadLocked();
            v = inst.videos.get(bvid);
        }
        return v == null ? List.of() : pick(v, BiliBiliUtil.pageOf(identifier), durationMs);
    }

    /** Whether this video was looked up (an empty result counts as known, so it is not asked again every time). */
    public static boolean isKnown(String identifier) {
        var bvid = BiliBiliUtil.stripPage(identifier);
        return bvid != null && !bvid.isBlank() && INSTANCE.fresh(bvid) != null;
    }

    /**
     * Content position → source position: add back the length of the segments **already skipped**. Note that the
     * comparison is against the *content* position: a segment's content start is its start minus everything skipped
     * before it, and comparing the raw start against the content position would land at the segment's end instead.
     */
    public static long contentToSource(long contentMs, List<Segment> segments) {
        long c = Math.max(0L, contentMs);
        long skipped = 0;
        for (var seg : segments) {
            var len = seg.lengthMs();
            if (len <= 0)
                continue;
            if (c < seg.startMs() - skipped)
                break;                          // this segment has not been reached yet
            skipped += len;
        }
        return c + skipped;
    }

    /**
     * Source time → content time (the coordinate of the internal queue): subtract what was skipped before, and for a
     * position inside a segment take the content position where that segment starts skipping. The inverse of
     * {@link #contentToSource(long, List)} (the two agree on their own convention at the boundaries).
     */
    public static long sourceToContent(long sourceMs, List<Segment> segments) {
        long s = Math.max(0L, sourceMs);
        long skipped = 0;
        for (var seg : segments) {
            var len = seg.lengthMs();
            if (len <= 0)
                continue;
            if (s < seg.startMs())
                break;                          // this segment has not been reached yet
            if (s < seg.endMs())
                return Math.max(0L, seg.startMs() - skipped);   // inside a segment ⇒ start from where the skip begins
            skipped += len;
        }
        return Math.max(0L, s - skipped);
    }

    /** The real source position to open the stream at when playback starts at {@code sourceMs}: inside a segment it lands on the segment's end. */
    public static long readStartFor(long sourceMs, List<Segment> segments) {
        long s = Math.max(0L, sourceMs);
        for (var seg : segments) {
            if (seg.lengthMs() <= 0)
                continue;
            if (s <= seg.startMs())
                break;
            if (s < seg.endMs())
                return seg.endMs();
        }
        return s;
    }

    /** The real content length = source length − the total length of the segments inside it. */
    public static long contentDuration(long sourceMs, List<Segment> segments) {
        if (sourceMs <= 0)
            return 0L;
        long skipped = 0;
        for (var seg : segments)
            skipped += Math.max(0L, Math.min(sourceMs, seg.endMs()) - Math.min(sourceMs, seg.startMs()));
        return Math.max(0L, sourceMs - skipped);
    }

    /**
     * Whether this block (source time {@code [sourceMs, sourceMs + chunkMs)}) lies **entirely** inside a segment; if so
     * it is dropped whole. A block that crosses a boundary is kept: leaving under two seconds of ad beats losing real
     * content.
     */
    public static boolean dropWholeChunk(long sourceMs, long chunkMs, List<Segment> segments) {
        var end = sourceMs + chunkMs;
        for (var seg : segments)
            if (seg.lengthMs() > 0 && sourceMs >= seg.startMs() && end <= seg.endMs())
                return true;
        return false;
    }

    // ------------------------------------------------------------------ Lookup and cache

    /** The video's record when it is cached and fresh; otherwise {@code null} (the caller decides whether to request it). */
    private Video fresh(String bvid) {
        synchronized (this) {
            loadLocked();
            var v = videos.get(bvid);
            return v != null && System.currentTimeMillis() - v.checkedAt() < TTL_MS ? v : null;
        }
    }

    /** The actual request. Every exception is swallowed: this data is a bonus and must never affect playback. */
    private void fetch(String bvid) {
        try {
            var request = HttpRequest.newBuilder(URI.create(ENDPOINT + bvid + "&categories=" + encode(CATEGORIES)))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", BiliBiliUtil.USER_AGENT)
                    .header("origin", "IamMusicPlayer")
                    .header("x-ext-version", "1")
                    .GET()
                    .build();
            var started = System.currentTimeMillis();
            var response = BiliBiliUtil.client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            List<Segment> found = List.of();
            if (response.statusCode() == 200)
                found = parse(response.body());
            // 404 means the video has no segments at all (per the API wiki) and is treated like 200 []; other status codes only get a log line.
            else if (response.statusCode() != 404)
                LinkDebug.log("BiliBili sponsor http", "bv", bvid, "httpStatus", response.statusCode());

            remember(bvid, found);
            LinkDebug.log("BiliBili sponsor", "bv", bvid, "segs", found.size(),
                    "ms", System.currentTimeMillis() - started, "from", "http");
        } catch (Throwable t) {
            LinkDebug.log("BiliBili sponsor FAIL", "bv", bvid, "ex", t.getClass().getSimpleName() + ": " + t.getMessage());
        } finally {
            inFlight.remove(bvid);
        }
    }

    private void remember(String bvid, List<Segment> segments) {
        synchronized (this) {
            loadLocked();
            var old = videos.get(bvid);
            var cids = old == null ? new LinkedHashMap<Integer, String>() : new LinkedHashMap<>(old.cids());
            videos.remove(bvid);
            videos.put(bvid, new Video(System.currentTimeMillis(), cids, List.copyOf(segments)));
            while (videos.size() > MAX_VIDEOS) {
                var first = videos.keySet().iterator().next();
                videos.remove(first);
            }
            dirty = true;
        }
        startWriter();
        if (segments.isEmpty())
            LinkDebug.log("BiliBili sponsor none", "bv", bvid, "why", "none");
    }

    /** Response body → segment table; nothing recognised means an empty table. */
    private static List<Segment> parse(String body) {
        try {
            var json = JsonParser.parseString(body);
            if (!json.isJsonArray())
                return List.of();
            JsonArray array = json.getAsJsonArray();
            var out = new ArrayList<Segment>(array.size());
            for (JsonElement e : array) {
                if (!e.isJsonObject())
                    continue;
                JsonObject o = e.getAsJsonObject();
                JsonArray seg = o.has("segment") && o.get("segment").isJsonArray() ? o.getAsJsonArray("segment") : null;
                if (seg == null || seg.size() < 2)
                    continue;
                long start = Math.round(seg.get(0).getAsDouble() * 1000d);
                long end = Math.round(seg.get(1).getAsDouble() * 1000d);
                if (end <= start)
                    continue;
                out.add(new Segment(start, end, str(o, "cid")));
            }
            out.sort((a, b) -> Long.compare(a.startMs(), b.startMs()));
            return List.copyOf(out);
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static String str(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull() || !o.get(key).isJsonPrimitive())
            return "";
        try {
            return o.get(key).getAsString();
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * Which of the video's segments belong to the part being played: a known cid is used directly; a response with a
     * single cid has no ambiguity; otherwise the answer's own {@code videoDuration} is matched against this track's
     * length (±2 s).
     */
    private static List<Segment> pick(Video v, int page, long durationMs) {
        var known = v.cids().get(Math.max(1, page));
        if (known != null && !known.isBlank()) {
            var out = new ArrayList<Segment>();
            for (var seg : v.segments())
                if (known.equals(seg.cid()))
                    out.add(seg);
            return mergeOverlapping(out);
        }

        var byCid = new LinkedHashMap<String, List<Segment>>();
        for (var seg : v.segments())
            byCid.computeIfAbsent(seg.cid(), k -> new ArrayList<>()).add(seg);
        if (byCid.size() == 1)
            return mergeOverlapping(byCid.values().iterator().next());
        if (byCid.size() > 1 && durationMs > 0) {
            LinkDebug.log("BiliBili sponsor nocid", "cidCount", byCid.size(), "wantMs", durationMs);
        }
        return List.of();
    }

    /**
     * Sorts one part's segments and unions the overlapping (or touching) ones. The service reports each category as its
     * own entry, so a sponsor and a self-promo segment may cover the same seconds; the time-axis maths adds every
     * length it is handed, so leaving an overlap in would count those seconds twice and shift the whole timeline.
     */
    private static List<Segment> mergeOverlapping(List<Segment> segments) {
        if (segments.size() < 2)
            return List.copyOf(segments);
        var sorted = new ArrayList<Segment>(segments);
        sorted.sort((a, b) -> Long.compare(a.startMs(), b.startMs()));
        var out = new ArrayList<Segment>(sorted.size());
        Segment current = null;
        for (var seg : sorted) {
            if (seg.lengthMs() <= 0)
                continue;
            if (current == null) {
                current = seg;
                continue;
            }
            if (seg.startMs() <= current.endMs()) {
                current = new Segment(current.startMs(), Math.max(current.endMs(), seg.endMs()), current.cid());
            } else {
                out.add(current);
                current = seg;
            }
        }
        if (current != null)
            out.add(current);
        return List.copyOf(out);
    }

    /** Segment table → the span table the player uses. */
    private static List<MusicSkipProvider.Span> spans(List<Segment> segments) {
        if (segments.isEmpty())
            return List.of();
        var out = new ArrayList<MusicSkipProvider.Span>(segments.size());
        for (var seg : segments)
            out.add(new MusicSkipProvider.Span(seg.startMs(), seg.endMs()));
        return List.copyOf(out);
    }

    private static String encode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ Persistence

    /** Writes go to a background thread; a cache that cannot be written never throws (same as {@code BiliBiliArcTags}). */
    private void startWriter() {
        synchronized (this) {
            if (writing)
                return;
            if (!dirty)
                return;
            writing = true;
        }
        var thread = new Thread(this::writeLoop, "IMP-BiliBili-Sponsor-writer");
        thread.setDaemon(true);
        try {
            thread.start();
        } catch (Throwable t) {
            synchronized (this) {
                writing = false;
            }
        }
    }

    private void writeLoop() {
        try {
            while (true) {
                Map<String, Video> snapshot;
                synchronized (this) {
                    if (!dirty) {
                        writing = false;
                        return;
                    }
                    snapshot = new LinkedHashMap<>(videos);
                    dirty = false;
                }
                if (!writeFile(serialize(snapshot)))
                    return;
            }
        } catch (Throwable t) {
            LinkDebug.log("BiliBili sponsor save FAIL", "ex", t.toString());
        } finally {
            synchronized (this) {
                writing = false;
                if (dirty)
                    startWriter();
            }
        }
    }

    private static String serialize(Map<String, Video> snapshot) {
        var root = new JsonObject();
        for (var e : snapshot.entrySet()) {
            var v = e.getValue();
            var one = new JsonObject();
            one.addProperty("t", v.checkedAt());
            var pages = new JsonObject();
            v.cids().forEach((page, cid) -> pages.addProperty(String.valueOf(page), cid));
            one.add("p", pages);
            var segs = new JsonArray();
            for (var seg : v.segments()) {
                var a = new JsonArray();
                a.add(seg.startMs());
                a.add(seg.endMs());
                a.add(seg.cid());
                segs.add(a);
            }
            one.add("s", segs);
            root.add(e.getKey(), one);
        }
        return root.toString();
    }

    private static boolean writeFile(String json) {
        try {
            var file = file();
            if (file == null)
                return false;
            Files.createDirectories(file.getParent());
            var tmp = file.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicUnsupported) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Reads the file; only called while holding the lock. A broken file counts as an empty table (a cache is not worth failing playback for). */
    private void loadLocked() {
        if (loaded)
            return;
        try {
            var file = file();
            if (file != null && Files.isRegularFile(file)) {
                var json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (json.isJsonObject()) {
                    for (var e : json.getAsJsonObject().entrySet()) {
                        if (!e.getValue().isJsonObject())
                            continue;
                        var one = e.getValue().getAsJsonObject();
                        var cids = new LinkedHashMap<Integer, String>();
                        if (one.has("p") && one.get("p").isJsonObject())
                            for (var p : one.getAsJsonObject("p").entrySet())
                                cids.put(num(p.getKey(), 1), p.getValue().getAsString());
                        var segs = new ArrayList<Segment>();
                        if (one.has("s") && one.get("s").isJsonArray())
                            for (JsonElement se : one.getAsJsonArray("s")) {
                                if (!se.isJsonArray() || se.getAsJsonArray().size() < 3)
                                    continue;
                                var a = se.getAsJsonArray();
                                segs.add(new Segment(a.get(0).getAsLong(), a.get(1).getAsLong(), a.get(2).getAsString()));
                            }
                        videos.put(e.getKey(), new Video(one.has("t") ? one.get("t").getAsLong() : 0L, cids, List.copyOf(segs)));
                    }
                }
            }
            LinkDebug.log("BiliBili sponsor loaded", "entries", videos.size(), "file", FILE_NAME);
        } catch (Throwable t) {
            LinkDebug.log("BiliBili sponsor load FAIL", "ex", t.toString());
            videos.clear();
        }
        loaded = true;
    }

    private static int num(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** The cache file: the mod folder under the game directory, the same place as the other caches. */
    private static Path file() {
        try {
            var folder = IamMusicPlayer.getConfig().IMPRFolder;
            if (folder == null || folder.isBlank())
                return null;
            return Path.of(folder).resolve(FILE_NAME);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Self-test hook: clears the memory and the "loaded" flag so a test can replay from scratch. */
    static void resetForTest() {
        var inst = INSTANCE;
        synchronized (inst) {
            inst.videos.clear();
            inst.loaded = false;
            inst.dirty = false;
        }
    }
}
