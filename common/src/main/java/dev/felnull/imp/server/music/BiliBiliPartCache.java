package dev.felnull.imp.server.music;

import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicSource;
import dev.felnull.imp.util.BiliBiliIds;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the server knows about Bilibili videos' parts, so a boombox can walk the parts of a multi-part video that is one
 * playlist entry; in-memory on purpose — a stale entry is worse than not knowing.
 */
public final class BiliBiliPartCache {
    /** Cap on remembered videos (a UP with 1000 videos ≈ a few hundred KB); insertion order is not kept, only the cap matters. */
    private static final int MAX_ENTRIES = 20000;

    /** One part of a video, as the client learned it. */
    public record Part(String name, long durationMs) {
    }

    private static final BiliBiliPartCache INSTANCE = new BiliBiliPartCache();

    private final Map<String, List<Part>> parts = new ConcurrentHashMap<>();

    private BiliBiliPartCache() {
    }

    public static BiliBiliPartCache getInstance() {
        return INSTANCE;
    }

    /**
     * Remembers one video's parts (called on the server thread from the packet handler).
     *
     * @return {@code true} when the stored list really changed — callers use that to skip work a repeated report
     *         cannot affect, since the packet may be sent as often as the client likes
     */
    public boolean remember(String bvid, List<Part> list) {
        var bare = BiliBiliIds.stripPage(bvid);
        if (bare == null || bare.isBlank() || list == null || list.isEmpty())
            return false;
        if (parts.size() >= MAX_ENTRIES && !parts.containsKey(bare))
            parts.clear();   // crude but safe: a full wipe is better than unbounded growth on a long-lived server
        var copy = List.copyOf(list);
        var previous = parts.put(bare, copy);
        return !copy.equals(previous);
    }

    /** How many parts we know this video to have ({@code 0} = never reported). */
    public int countOf(@Nullable String identifier) {
        var list = parts.get(BiliBiliIds.stripPage(identifier) == null ? "" : BiliBiliIds.stripPage(identifier));
        return list == null ? 0 : list.size();
    }

    /** The remembered parts of one video, or an empty list. */
    public List<Part> partsOf(@Nullable String identifier) {
        var bare = BiliBiliIds.stripPage(identifier);
        if (bare == null)
            return List.of();
        var list = parts.get(bare);
        return list == null ? List.of() : list;
    }

    /**
     * The music to play after {@code current} when its video has a following part, or {@code null} to move on to the next entry; it keeps the entry's uuid and is not added to any playlist.
     */
    @Nullable
    public Music nextPartMusic(@Nullable Music current, long createDate) {
        if (current == null || current.getSource() == null)
            return null;
        // The last part returns null: the playlist takes over again.
        return partMusic(current, BiliBiliIds.pageOf(current.getSource().getIdentifier()) + 1, createDate);
    }

    /**
     * The music for one part ({@code page} 1-based), or {@code null} (single-part video, never reported, out of range); the automatic continuation and the part-jump hotkey share it, so both produce identical musics.
     */
    @Nullable
    public Music partMusic(@Nullable Music current, int page, long createDate) {
        if (current == null || current.getSource() == null)
            return null;
        var source = current.getSource();
        var list = partsOf(source.getIdentifier());
        if (list.size() <= 1 || page < 1 || page > list.size())
            return null;
        var part = list.get(page - 1);
        var image = current.getImage() == null ? ImageInfo.EMPTY : current.getImage();
        return new Music(current.getUuid(),
                partName(videoTitle(current, list), part, page, list.size()),
                current.getAuthor(),
                new MusicSource(source.getLoaderType(), BiliBiliIds.withPage(source.getIdentifier(), page),
                        Math.max(0L, part.durationMs())),
                image,
                current.getOwner(),
                createDate);
    }

    /**
     * The video's own title behind {@code current}: a synthesized part already carries {@code title · part name}, so
     * taking the name as-is would give {@code title · P2 · P3} and keep growing; part 1 is the entry itself.
     */
    private static String videoTitle(Music current, List<Part> list) {
        var name = current.getName() == null ? "" : current.getName();
        var identifier = current.getSource() == null ? null : current.getSource().getIdentifier();
        int curPage = BiliBiliIds.pageOf(identifier);
        if (curPage <= 1 || curPage > list.size())
            return name;
        var self = list.get(curPage - 1);
        var selfName = self == null || self.name() == null ? "" : self.name().strip();
        var byName = " · " + selfName;
        if (!selfName.isEmpty() && name.endsWith(byName))
            return name.substring(0, name.length() - byName.length());
        var byMarker = " (P" + curPage + "/" + list.size() + ")";
        if (name.endsWith(byMarker))
            return name.substring(0, name.length() - byMarker.length());
        return name;
    }


    /** The identifier of the part that would follow {@code current}, or {@code null} (used to see whether the playlist already has that part as a row). */
    @Nullable
    public String nextPartIdentifier(@Nullable Music current) {
        var next = nextPartMusic(current, 0L);
        return next == null || next.getSource() == null ? null : next.getSource().getIdentifier();
    }

    /** The same rule the client uses for the playlist rows: {@code title · part name}, or {@code title (P2/7)} when it would repeat itself. */
    public static String partName(String videoTitle, Part part, int page, int total) {
        var title = videoTitle == null ? "" : videoTitle.strip();
        var name = part == null || part.name() == null ? "" : part.name().strip();
        if (name.isEmpty() || name.equals(title))
            return title + " (P" + page + "/" + total + ")";
        return title.isEmpty() ? name : title + " · " + name;
    }
}
