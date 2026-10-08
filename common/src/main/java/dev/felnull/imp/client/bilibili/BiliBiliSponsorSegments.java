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
 * B站 SponsorBlock（哔哩屏蔽团 {@code bsbsb.top}）片段表：内容时间↔源时间换算；**任何失败都当「没有片段」**（播放照旧）。
 * 协议 {@code /api/skipSegments?videoID=<bvid>&categories=[…]} 无需鉴权头；无数据是 {@code 200 []}，片段单位是**秒**。
 */
public final class BiliBiliSponsorSegments {
    public static final String FILE_NAME = "bilibili_sponsor_segments.json";
    /** 只跳「赞助/恰饭」与「无偿/自我推广」两类；interaction（三连提醒）等留在原地。 */
    private static final String CATEGORIES = "[\"sponsor\",\"selfpromo\"]";
    private static final String ENDPOINT = "https://bsbsb.top/api/skipSegments?videoID=";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    /** 众包数据会变；一周后重新问一次（过期只影响"要不要跳"，不影响播放）。 */
    private static final long TTL_MS = 7L * 24 * 60 * 60 * 1000;
    /** 缓存文件上限，超出先丢最早查的（每条视频几十字节）。 */
    private static final int MAX_VIDEOS = 5000;

    /** 源时间毫秒的一段；{@code cid} 说明它属于哪个分P。 */
    public record Segment(long startMs, long endMs, String cid) {
        public long lengthMs() {
            return Math.max(0L, endMs - startMs);
        }
    }

    /** 一条视频查回来的东西：查的时间、分P → cid（播放时顺手记的）、全部片段（含各分P）。 */
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

    /** 开关（默认关）；读不到配置时当关。 */
    public static boolean isEnabled() {
        try {
            return IamMusicPlayer.getConfig().bilibiliConfig.skipSponsorSegments;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 给播放器用的跳段表；不适用时返回 {@link MusicSkipProvider#NONE}（非 B站 / 直播 / 开关关）。
     * 音响那条链**也走这里**：服务端的时间轴同样按「内容」走，两边算出的位置才是同一个。
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

    /** 一条曲子的跳段表（片段查到之后缓存下来：{@code sourcePositionOf} 每帧都会被问到）。 */
    private static final class Provider implements MusicSkipProvider {
        private final String identifier;
        private final long durationMs;
        /** {@code null} = 还没解析出结果（可能正在查）；空表 = 查过了、这条视频没有片段。 */
        private volatile List<Segment> resolved;
        /** Read head of the audio reader (source ms), reported by {@link #skips(long)}; 0 = nothing read yet. */
        private volatile long readHeadMs;

        private Provider(String identifier, long durationMs) {
            this.identifier = identifier;
            this.durationMs = durationMs;
        }

        /** 片段表（快照）。{@code mayBlock} 只对"从曲中开始放"为真 —— 那时位置算错了会直接播错地方。 */
        private List<Segment> segments(boolean mayBlock) {
            var now = resolved;
            if (now != null)
                return now;
            // 从 0 开始放时不必等：查回来晚一点也只是"前面一小段广告照放"。
            ensure(identifier, durationMs, mayBlock);
            var segments = clipToReadHead(segmentsFor(identifier, durationMs));
            if (!segments.isEmpty() || isKnown(identifier)) {
                resolved = segments;
                reportIfNeeded(identifier, segments);
            }
            return segments;
        }

        /**
         * 查回来时读头已经越过的那截按「已经照样播出去」处理：把片段起点夹到读头（整段在身后就剔除）。
         * 否则那部分没丢的也算成跳过量 —— 对外位置永久偏移、曲尾被真截掉。只做一次。
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
            // 记下读头：万一片段表是"读到一半才到"的，上面那次夹取就知道该从哪儿算起。
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
     * 把这份片段表报给服务端一次（音响的时间轴按它收尾：内容时长 = 源时长 − Σ跳过）；一个会话每个标识符只报一次，
     * 送不出去就把标记撤掉，下次播放再试。
     */
    private static void reportIfNeeded(String identifier, List<Segment> segments) {
        report(identifier, segments, false);
    }

    /**
     * @param allowEmpty 空表也发（服务端收到空表就忘掉这条 → 不再替这个曲子跳位置），开关被关掉时用
     */
    private static void report(String identifier, List<Segment> segments, boolean allowEmpty) {
        if (identifier == null || identifier.isBlank() || (segments.isEmpty() && !allowEmpty))
            return;
        if (allowEmpty) {
            // ⚠️ 这一路必须**绕过**"一个会话只报一次"的闸门：能走到这里说明本会话报过（服务端手里可能还留着那条），
            // 而被那闸门挡下就正是"关了开关、服务端还照跳"的原因。清掉标记，之后重新打开开关还能再报。
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

    /** 这个会话里已经报给服务端的标识符。 */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    /** 记住"这首歌是哪个分P 的 cid"（加载时顺手记，见 {@code BiliBiliUtil.fetchAudioCandidates}）。 */
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

    /** 查一次（必要时），返回当前已知的片段；{@code block} = 允许在调用线程上等这次网络请求。 */
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

    /** 当前缓存的片段（不查网络），已按分P 归好类。 */
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

    /** 这条视频查过没有（查过但没片段也算"知道"，免得每次都重问一遍）。 */
    public static boolean isKnown(String identifier) {
        var bvid = BiliBiliUtil.stripPage(identifier);
        return bvid != null && !bvid.isBlank() && INSTANCE.fresh(bvid) != null;
    }

    /**
     * 内容位置 → 源位置：把**已经跳过**的片段长度加回去。注意比的是「内容位置」：一段的内容起点 = 段起点 − 它前面已跳掉的量，
     * 拿段起点直接跟内容位置比会落到片段尾，而不是正确的内容位置。
     */
    public static long contentToSource(long contentMs, List<Segment> segments) {
        long c = Math.max(0L, contentMs);
        long skipped = 0;
        for (var seg : segments) {
            var len = seg.lengthMs();
            if (len <= 0)
                continue;
            if (c < seg.startMs() - skipped)
                break;                          // 还没听到这一段
            skipped += len;
        }
        return c + skipped;
    }

    /**
     * 视频时间 → 内容时间（内部队列的坐标）：把前面整段跳掉的长度减掉；落在片段里就取「这一段开始跳」的那个内容位置。
     * 与 {@link #contentToSource(long, List)} 互为反函数（边界上按各自的约定取值）。
     */
    public static long sourceToContent(long sourceMs, List<Segment> segments) {
        long s = Math.max(0L, sourceMs);
        long skipped = 0;
        for (var seg : segments) {
            var len = seg.lengthMs();
            if (len <= 0)
                continue;
            if (s < seg.startMs())
                break;                          // 还没走到这一段
            if (s < seg.endMs())
                return Math.max(0L, seg.startMs() - skipped);   // 落在片段里 ⇒ 从"开始跳"的那个内容位置起
            skipped += len;
        }
        return Math.max(0L, s - skipped);
    }

    /** 从视频位置 {@code sourceMs} 开始放时真正该开流的源位置：落在片段里就落到片段尾。 */
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

    /** 内容的实际长度 = 源长度 − 落在里面的片段总长。 */
    public static long contentDuration(long sourceMs, List<Segment> segments) {
        if (sourceMs <= 0)
            return 0L;
        long skipped = 0;
        for (var seg : segments)
            skipped += Math.max(0L, Math.min(sourceMs, seg.endMs()) - Math.min(sourceMs, seg.startMs()));
        return Math.max(0L, sourceMs - skipped);
    }

    /**
     * 这一块（源时间 {@code [sourceMs, sourceMs + chunkMs)}）是否**整块**落在某个片段里 —— 是就整块丢掉。
     * 跨边界的那一块保留：宁可残留不到两秒广告，也不丢正片内容。
     */
    public static boolean dropWholeChunk(long sourceMs, long chunkMs, List<Segment> segments) {
        var end = sourceMs + chunkMs;
        for (var seg : segments)
            if (seg.lengthMs() > 0 && sourceMs >= seg.startMs() && end <= seg.endMs())
                return true;
        return false;
    }

    // ------------------------------------------------------------------ 查询与缓存

    /** 已缓存且没过期 → 该视频的记录；否则 {@code null}（调用方决定要不要发请求）。 */
    private Video fresh(String bvid) {
        synchronized (this) {
            loadLocked();
            var v = videos.get(bvid);
            return v != null && System.currentTimeMillis() - v.checkedAt() < TTL_MS ? v : null;
        }
    }

    /** 真正发请求。异常一律吞掉：这是锦上添花的数据，不能影响播放。 */
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
            // 404 = 这条视频一条片段都没有（wiki 的说法），和 200 [] 一样处理；其它状态码只记一行。
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

    /** 响应体 → 片段表；一条也认不出就是空表。 */
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
     * 这条视频的片段里，哪些属于我们在放的那一 P：已知 cid → 直接用它；响应里只有一种 cid → 没有歧义；
     * 否则拿片段自带的 {@code videoDuration} 和本曲时长对（±2 秒）。
     */
    private static List<Segment> pick(Video v, int page, long durationMs) {
        var known = v.cids().get(Math.max(1, page));
        if (known != null && !known.isBlank()) {
            var out = new ArrayList<Segment>();
            for (var seg : v.segments())
                if (known.equals(seg.cid()))
                    out.add(seg);
            return List.copyOf(out);
        }

        var byCid = new LinkedHashMap<String, List<Segment>>();
        for (var seg : v.segments())
            byCid.computeIfAbsent(seg.cid(), k -> new ArrayList<>()).add(seg);
        if (byCid.size() == 1)
            return List.copyOf(byCid.values().iterator().next());
        if (byCid.size() > 1 && durationMs > 0) {
            LinkDebug.log("BiliBili sponsor nocid", "cidCount", byCid.size(), "wantMs", durationMs);
        }
        return List.of();
    }

    /** 片段表 → 播放器用的跨度表。 */
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

    // ------------------------------------------------------------------ 落盘

    /** 写盘交给后台线程；缓存写不进去也绝不抛（同 {@code BiliBiliArcTags} 的做法）。 */
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

    /** 读盘；只在持锁时调用。坏文件当空表（缓存不值得让播放失败）。 */
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

    /** 缓存文件：与其它缓存同一个"游戏目录下的模组文件夹"。 */
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

    /** 自检钩子：清掉内存与"已加载"标记，供单元自检重放。 */
    static void resetForTest() {
        var inst = INSTANCE;
        synchronized (inst) {
            inst.videos.clear();
            inst.loaded = false;
            inst.dirty = false;
        }
    }
}
