package dev.felnull.imp.client.bilibili;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.link.LinkDebug;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** "What is this video" cache: {@code bvid → {合集(s), 父分区, 分P 表}}, filled at import/play time as imported music
 * lacks it. One JSON file, safe to delete; no entry = "never looked", one element = "looked up: single part". */
public final class BiliBiliArcTags {
    private static final String FILE_NAME = "bilibili_arc_tags.json";
    /** Cap on remembered videos; the oldest entries are dropped first (20k × ~60 B ≈ 1.2 MB, far above real use). */
    private static final int MAX_ENTRIES = 20000;

    /**
     * One part (分P) of a video: what the address API reports per page.
     *
     * @param name       the part's own title ({@code pages[].part}); empty when Bilibili has none
     * @param durationMs that part's length in milliseconds
     * @param firstFrame URL of that part's first frame, empty when the API gives none
     */
    public record Part(String name, long durationMs, String firstFrame) {
    }

    /**
     * The video-level facts of one video, all taken from the same {@code view} response the part list comes from.
     *
     * @param title      {@code data.title}
     * @param author     {@code data.owner.name}
     * @param cover      {@code data.pic}, {@code http://} rewritten to {@code https://}
     * @param zone       {@code data.tname}
     * @param views      {@code data.stat.view}
     * @param danmaku    {@code data.stat.danmaku}
     * @param likes      {@code data.stat.like}
     * @param pubdateMs  {@code data.pubdate} in seconds, converted to ms
     * @param desc       {@code data.desc}
     * @param durationMs {@code data.duration} in seconds, converted to ms
     * @param replies    {@code data.stat.reply}
     * @param coins      {@code data.stat.coin}
     * @param favorites  {@code data.stat.favorite}
     * @param shares     {@code data.stat.share}
     */
    public record Info(String title, String author, String cover, String zone, long views, long danmaku, long likes,
                       long pubdateMs, String desc, long durationMs, long replies, long coins, long favorites,
                       long shares) {
        public static final Info EMPTY = new Info("", "", "", "", 0L, 0L, 0L, 0L, "", 0L, 0L, 0L, 0L, 0L);

        public boolean isEmpty() {
            return (title == null || title.isBlank()) && (author == null || author.isBlank())
                    && (cover == null || cover.isBlank()) && (zone == null || zone.isBlank())
                    && (desc == null || desc.isBlank())
                    && views <= 0L && danmaku <= 0L && likes <= 0L && pubdateMs <= 0L && durationMs <= 0L
                    && replies <= 0L && coins <= 0L && favorites <= 0L && shares <= 0L;
        }
    }

    /** One remembered video. {@code pubdateMs} = the upload time the import list carried (the {@code view} value lands in {@link Info#pubdateMs()}). */
    public record Tag(String zone, Set<String> seasons, List<Part> parts, Info info, long pubdateMs, long checkedAt,
                      boolean gone) {
        public boolean isEmpty() {
            return (zone == null || zone.isBlank())
                    && (seasons == null || seasons.isEmpty())
                    && (parts == null || parts.isEmpty())
                    && (info == null || info.isEmpty())
                    && pubdateMs <= 0L
                    // A tag that only says "gone" or "checked at" is still worth keeping — dropping it would make the
                    // sweep ask Bilibili about that video again.
                    && !gone && checkedAt <= 0L;
        }
    }

    private static final BiliBiliArcTags INSTANCE = new BiliBiliArcTags();

    private final Map<String, Tag> tags = new LinkedHashMap<>();
    private boolean loaded;
    private boolean dirty;

    private BiliBiliArcTags() {
        // The file is a few hundred KB: parsing it on whichever thread asks first would stall that thread — and the
        // first asker is the render thread (the 分P badge / the playlist's category filter run every tick). Load it in
        // the background right away instead; a reader that still arrives too early simply waits on the monitor.
        var thread = new Thread(this::load, "IMP-BiliBili-ArcTags-load");
        thread.setDaemon(true);
        thread.start();
    }

    public static BiliBiliArcTags getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------ reading

    /** The remembered tag of one video, or {@code null} when we never saw it. */
    public synchronized Tag get(String bvid) {
        load();
        if (bvid == null || bvid.isBlank())
            return null;
        var tag = tags.get(bvid);
        return tag == null || tag.isEmpty() ? null : tag;
    }

    public synchronized String zoneOf(String bvid) {
        var tag = get(bvid);
        return tag == null || tag.zone() == null ? "" : tag.zone();
    }

    public synchronized Set<String> seasonsOf(String bvid) {
        var tag = get(bvid);
        return tag == null || tag.seasons() == null ? Set.of() : Set.copyOf(tag.seasons());
    }

    /** Part list: empty = never looked / single part / lookup failed, i.e. "unknown" — never "definitely one part". */
    public synchronized List<Part> partsOf(String bvid) {
        var tag = get(bvid);
        return tag == null || tag.parts() == null ? List.of() : tag.parts();
    }

    /** How many videos are remembered (the manage page shows nothing about this; it is for diagnostics). */
    public synchronized int size() {
        load();
        return tags.size();
    }

    // ------------------------------------------------------------------ writing

    /** Remembers one video's 合集/分区 (import path, client thread); an existing part list is kept. Only this path and
     * {@link #flush()} touch the map, and both are synchronized. */
    public synchronized void remember(String bvid, String zone, Collection<String> seasons, long pubdateMs) {
        if (bvid == null || bvid.isBlank())
            return;
        load();
        var set = new LinkedHashSet<String>();
        if (seasons != null) {
            for (var s : seasons) {
                if (s != null && !s.isBlank())
                    set.add(s);
            }
        }
        put(bvid, new Tag(zone == null ? "" : zone, Set.copyOf(set), partsOfRaw(bvid), infoOfRaw(bvid), pubdateMs,
                checkedAtRaw(bvid), goneRaw(bvid)));
    }

    /** Remembers the video-level facts of one video (title / uploader / cover / counts / publish time).
     * Called from the same {@code view} response as the part list (no extra request); an existing part list is kept. */
    public synchronized void rememberInfo(String bvid, Info info) {
        if (bvid == null || bvid.isBlank() || info == null || info.isEmpty())
            return;
        load();
        var old = tags.get(bvid);
        put(bvid, new Tag(old == null ? "" : old.zone(), old == null ? Set.of() : old.seasons(),
                old == null ? List.of() : old.parts(), info, old == null ? 0L : old.pubdateMs(),
                old == null ? 0L : old.checkedAt(), old != null && old.gone()));
    }

    /** Records only the upload time — {@link #remember} would blank the 分区/合集 this video already has. */
    public synchronized void rememberPubdate(String bvid, long pubdateMs) {
        if (bvid == null || bvid.isBlank() || pubdateMs <= 0L)
            return;
        load();
        var old = tags.get(bvid);
        put(bvid, new Tag(old == null ? "" : old.zone(), old == null ? Set.of() : old.seasons(),
                old == null ? List.of() : old.parts(), old == null ? Info.EMPTY : old.info(), pubdateMs,
                old == null ? 0L : old.checkedAt(), old != null && old.gone()));
    }

    /**
     * Records that a {@code view} lookup succeeded, with the time: the dead-link sweep skips videos asked about recently ({@code DEAD_SCAN_TTL_MS}) instead of one request per song on every press.
     */
    public synchronized void markChecked(String bvid) {
        if (bvid == null || bvid.isBlank())
            return;
        load();
        var old = tags.get(bvid);
        put(bvid, new Tag(old == null ? "" : old.zone(), old == null ? Set.of() : old.seasons(),
                old == null ? List.of() : old.parts(), old == null ? Info.EMPTY : old.info(),
                old == null ? 0L : old.pubdateMs(),
                System.currentTimeMillis(), old != null && old.gone()));
    }

    /** Records Bilibili's "gone" verdict ({@code -404}); persisted so 「扫失效」 can tick the row without asking again,
     * and {@code gone = false} clears the mark (restored uploads happen). */
    public synchronized void markGone(String bvid, boolean gone) {
        if (bvid == null || bvid.isBlank())
            return;
        load();
        var old = tags.get(bvid);
        put(bvid, new Tag(old == null ? "" : old.zone(), old == null ? Set.of() : old.seasons(),
                old == null ? List.of() : old.parts(), old == null ? Info.EMPTY : old.info(),
                old == null ? 0L : old.pubdateMs(),
                System.currentTimeMillis(), gone));
    }

    /** Whether this video is one Bilibili reported as gone (see {@link #markGone}). */
    public synchronized boolean isGone(String bvid) {
        return get(bvid) != null && tags.get(bvid).gone();
    }

    /** When this video's {@code view} was last answered, in epoch ms ({@code 0} = never). */
    public synchronized long checkedAt(String bvid) {
        var tag = get(bvid);
        return tag == null ? 0L : tag.checkedAt();
    }

    /** {@link #checkedAt} while already holding the lock. */
    private long checkedAtRaw(String bvid) {
        var tag = tags.get(bvid);
        return tag == null ? 0L : tag.checkedAt();
    }

    /** {@link #isGone} while already holding the lock. */
    private boolean goneRaw(String bvid) {
        var tag = tags.get(bvid);
        return tag != null && tag.gone();
    }

    /** Upload time in epoch ms ({@code 0} = unknown): the {@code view} value when we have it, else the import list's. */
    public synchronized long pubdateOf(String bvid) {
        var tag = get(bvid);
        if (tag == null)
            return 0L;
        var info = tag.info();
        return info != null && info.pubdateMs() > 0L ? info.pubdateMs() : tag.pubdateMs();
    }

    /** The remembered video-level facts, or {@link Info#EMPTY} when we never saw them. */
    public synchronized Info infoOf(String bvid) {
        var tag = get(bvid);
        return tag == null || tag.info() == null ? Info.EMPTY : tag.info();
    }

    /** The remembered video-level facts while already holding the lock (avoids a nested {@link #get} call). */
    private Info infoOfRaw(String bvid) {
        var tag = tags.get(bvid);
        return tag == null || tag.info() == null ? Info.EMPTY : tag.info();
    }

    /** Remembers a video's parts (media layer and the "展开分P" action); a one-element list is stored too and means
     * "looked up: single part" — that is what stops 展开分P from re-requesting single-part videos. */
    public synchronized void rememberParts(String bvid, List<Part> parts) {
        if (bvid == null || bvid.isBlank() || parts == null || parts.isEmpty())
            return;
        load();
        var old = tags.get(bvid);
        put(bvid, new Tag(old == null ? "" : old.zone(), old == null ? Set.of() : old.seasons(), List.copyOf(parts),
                old == null ? Info.EMPTY : old.info(), old == null ? 0L : old.pubdateMs(),
                old == null ? 0L : old.checkedAt(), old != null && old.gone()));
    }

    /** The raw part list of one video while already holding the lock (avoids a nested {@link #get} call). */
    private List<Part> partsOfRaw(String bvid) {
        var tag = tags.get(bvid);
        return tag == null || tag.parts() == null ? List.of() : tag.parts();
    }

    private void put(String bvid, Tag tag) {
        if (tag.isEmpty())
            return;
        // Re-insert so insertion order stays the recency order used by the cap
        tags.remove(bvid);
        tags.put(bvid, tag);
        while (tags.size() > MAX_ENTRIES) {
            var oldest = tags.keySet().iterator();
            if (!oldest.hasNext())
                break;
            oldest.next();
            oldest.remove();
        }
        dirty = true;
    }

    /** Async cache save (background daemon thread); the synchronized {@link #partsOf}/{@link #zoneOf} run per tick on
     * the render thread, so it must not write under the lock: the map is shallow-copied there and bursts coalesce. */
    public void flush() {
        synchronized (this) {
            if (!dirty)
                return;
        }
        startWriter();
    }

    /** True while a writer thread owns the job (a second request is folded into the running one). */
    private final java.util.concurrent.atomic.AtomicBoolean writing = new java.util.concurrent.atomic.AtomicBoolean();

    private void startWriter() {
        if (!writing.compareAndSet(false, true))
            return;
        var thread = new Thread(this::writeLoop, "IMP-BiliBili-ArcTags-writer");
        thread.setDaemon(true);
        try {
            thread.start();
        } catch (Throwable t) {
            writing.set(false);
        }
    }

    /** Writes the cache with a temp file + move (atomic, so a crash mid-write cannot leave a half file); never throws — a cache is not worth failing an import or a play for. */
    private void writeLoop() {
        try {
            while (true) {
                Map<String, Tag> snapshot;
                synchronized (this) {
                    if (!dirty)
                        return;
                    // Shallow copy: the Tag/Part records are immutable, so the JSON can be built outside the lock
                    snapshot = new LinkedHashMap<>(tags);
                    dirty = false;
                }
                if (!writeFile(serialize(snapshot)))
                    return;
                LinkDebug.log("BiliBili arc tags saved", "entries", snapshot.size(), "file", FILE_NAME);
            }
        } catch (Throwable t) {
            LinkDebug.log("BiliBili arc tags save FAIL", "ex", t.toString());
        } finally {
            writing.set(false);
            // A change may have landed after the last "not dirty" check but before the flag was cleared: re-arm once
            synchronized (this) {
                if (dirty)
                    startWriter();
            }
        }
    }

    /** The whole cache as JSON text (no lock needed: it only reads the snapshot it is given). */
    private static String serialize(Map<String, Tag> snapshot) {
        var root = new JsonObject();
        for (var e : snapshot.entrySet())
            root.add(e.getKey(), toJson(e.getValue()));
        return root.toString();
    }

    /** The slow part, strictly outside the lock. Never throws; {@code false} = the write failed. */
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
            LinkDebug.log("BiliBili arc tags save FAIL", "ex", t.toString());
            return false;
        }
    }

    // ------------------------------------------------------------------ storage

    private synchronized void load() {
        if (loaded)
            return;
        // `loaded` is set after the parse on purpose: setting it first handed any reader arriving during the parse an empty map,
        // and "empty" means "we know nothing about this video" to every caller. Constructor already preloads, so this wait is rare.
        try {
            var file = file();
            if (file == null || !Files.isRegularFile(file)) {
                loaded = true;
                return;
            }
            var json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!json.isJsonObject()) {
                loaded = true;
                return;
            }
            for (var e : json.getAsJsonObject().entrySet()) {
                if (!e.getValue().isJsonObject())
                    continue;
                tags.put(e.getKey(), fromJson(e.getValue().getAsJsonObject()));
            }
            LinkDebug.log("BiliBili arc tags loaded", "entries", tags.size(), "file", FILE_NAME);
        } catch (Throwable t) {
            // A damaged cache must never break the UI; start from an empty map instead
            LinkDebug.log("BiliBili arc tags load FAIL", "ex", t.toString());
            tags.clear();
        }
        loaded = true;
    }

    /** The cache file — the same "mod folder under the game directory" the name cache uses. */
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

    /** One entry → its JSON form: {@code {"z":分区,"pd":投稿毫秒,"s":[合集…],"p":[{"n":分P名,"d":毫秒,"f":首帧}]}};
     * package-private so the self-test can round-trip it against {@link #fromJson} without touching the file. */
    static JsonObject toJson(Tag tag) {
        var one = new JsonObject();
        if (tag.zone() != null && !tag.zone().isBlank())
            one.addProperty("z", tag.zone());
        // "gone" and "checked at" ride along in the same entry: they are what makes 「扫失效」 instant on a repeat press
        if (tag.checkedAt() > 0L)
            one.addProperty("at", tag.checkedAt());
        if (tag.gone())
            one.addProperty("g", true);
        // top-level "pd" = from the import list; the "pd" inside "i" = from the `view` response
        if (tag.pubdateMs() > 0L)
            one.addProperty("pd", tag.pubdateMs());
        if (tag.seasons() != null && !tag.seasons().isEmpty()) {
            var arr = new JsonArray();
            for (var s : tag.seasons())
                arr.add(s);
            one.add("s", arr);
        }
        if (tag.parts() != null && !tag.parts().isEmpty()) {
            var arr = new JsonArray();
            for (var p : tag.parts()) {
                var po = new JsonObject();
                po.addProperty("n", p.name() == null ? "" : p.name());
                po.addProperty("d", p.durationMs());
                if (p.firstFrame() != null && !p.firstFrame().isBlank())
                    po.addProperty("f", p.firstFrame());
                arr.add(po);
            }
            one.add("p", arr);
        }
        var info = tag.info();
        if (info != null && !info.isEmpty()) {
            var io = new JsonObject();
            if (!info.title().isBlank())
                io.addProperty("t", info.title());
            if (!info.author().isBlank())
                io.addProperty("a", info.author());
            if (!info.cover().isBlank())
                io.addProperty("c", info.cover());
            if (!info.zone().isBlank())
                io.addProperty("z2", info.zone());
            if (info.views() > 0L)
                io.addProperty("v", info.views());
            if (info.danmaku() > 0L)
                io.addProperty("dm", info.danmaku());
            if (info.likes() > 0L)
                io.addProperty("l", info.likes());
            if (info.pubdateMs() > 0L)
                io.addProperty("pd", info.pubdateMs());
            if (info.durationMs() > 0L)
                io.addProperty("du", info.durationMs());
            if (info.replies() > 0L)
                io.addProperty("rp", info.replies());
            if (info.coins() > 0L)
                io.addProperty("cn", info.coins());
            if (info.favorites() > 0L)
                io.addProperty("fv", info.favorites());
            if (info.shares() > 0L)
                io.addProperty("sh", info.shares());
            if (!info.desc().isBlank())
                io.addProperty("ds", info.desc());
            if (io.size() > 0)
                one.add("i", io);
        }
        return one;
    }

    /** JSON → one entry. Anything missing or of the wrong type reads as "not known", never as an exception. */
    static Tag fromJson(JsonObject one) {
        var zone = one.has("z") && !one.get("z").isJsonNull() ? one.get("z").getAsString() : "";
        var seasons = new LinkedHashSet<String>();
        if (one.has("s") && one.get("s").isJsonArray()) {
            for (var s : one.getAsJsonArray("s")) {
                if (!s.isJsonNull() && !s.getAsString().isBlank())
                    seasons.add(s.getAsString());
            }
        }
        var parts = new ArrayList<Part>();
        if (one.has("p") && one.get("p").isJsonArray()) {
            for (var pe : one.getAsJsonArray("p")) {
                if (!pe.isJsonObject())
                    continue;
                var po = pe.getAsJsonObject();
                parts.add(new Part(
                        po.has("n") && !po.get("n").isJsonNull() ? po.get("n").getAsString() : "",
                        po.has("d") && !po.get("d").isJsonNull() ? po.get("d").getAsLong() : 0L,
                        po.has("f") && !po.get("f").isJsonNull() ? po.get("f").getAsString() : ""));
            }
        }
        var info = Info.EMPTY;
        if (one.has("i") && one.get("i").isJsonObject()) {
            var io = one.getAsJsonObject("i");
            info = new Info(
                    io.has("t") && !io.get("t").isJsonNull() ? io.get("t").getAsString() : "",
                    io.has("a") && !io.get("a").isJsonNull() ? io.get("a").getAsString() : "",
                    io.has("c") && !io.get("c").isJsonNull() ? io.get("c").getAsString() : "",
                    io.has("z2") && !io.get("z2").isJsonNull() ? io.get("z2").getAsString() : "",
                    io.has("v") && !io.get("v").isJsonNull() ? io.get("v").getAsLong() : 0L,
                    io.has("dm") && !io.get("dm").isJsonNull() ? io.get("dm").getAsLong() : 0L,
                    io.has("l") && !io.get("l").isJsonNull() ? io.get("l").getAsLong() : 0L,
                    io.has("pd") && !io.get("pd").isJsonNull() ? io.get("pd").getAsLong() : 0L,
                    io.has("ds") && !io.get("ds").isJsonNull() ? io.get("ds").getAsString() : "",
                    io.has("du") && !io.get("du").isJsonNull() ? io.get("du").getAsLong() : 0L,
                    io.has("rp") && !io.get("rp").isJsonNull() ? io.get("rp").getAsLong() : 0L,
                    io.has("cn") && !io.get("cn").isJsonNull() ? io.get("cn").getAsLong() : 0L,
                    io.has("fv") && !io.get("fv").isJsonNull() ? io.get("fv").getAsLong() : 0L,
                    io.has("sh") && !io.get("sh").isJsonNull() ? io.get("sh").getAsLong() : 0L);
        }
        return new Tag(zone, Set.copyOf(seasons), List.copyOf(parts), info,
                one.has("pd") && !one.get("pd").isJsonNull() ? one.get("pd").getAsLong() : 0L,
                one.has("at") && !one.get("at").isJsonNull() ? one.get("at").getAsLong() : 0L,
                one.has("g") && !one.get("g").isJsonNull() && one.get("g").getAsBoolean());
    }

    /** Diagnostics only: the categories currently known for a playlist's musics, biggest first. */
    public synchronized List<String> knownSeasons() {
        load();
        var out = new LinkedHashSet<String>();
        for (var tag : tags.values())
            out.addAll(tag.seasons());
        return List.copyOf(out);
    }
}
