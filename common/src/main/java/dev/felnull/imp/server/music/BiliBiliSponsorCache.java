package dev.felnull.imp.server.music;

import dev.felnull.imp.util.BiliBiliIds;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Bilibili sponsor/ad segments the server knows about (reported by a client). They only push the boombox position
 * **past a segment**; a dedicated server never contacts {@code bsbsb.top} itself, and in-memory state is enough.
 */
public final class BiliBiliSponsorCache {
    /** Cap on remembered tracks (a few hundred bytes each); a full wipe is safer than unbounded growth. */
    private static final int MAX_ENTRIES = 20000;

    private static final BiliBiliSponsorCache INSTANCE = new BiliBiliSponsorCache();

    /** Identifier (normalised to {@code bvid-pN}) → the segments to skip (source-time milliseconds, {@code [start,end]}). */
    private final Map<String, List<long[]>> segments = new ConcurrentHashMap<>();

    private BiliBiliSponsorCache() {
    }

    public static BiliBiliSponsorCache getInstance() {
        return INSTANCE;
    }

    /** Remembers one track's segments (server thread, from the packet handler). An empty list clears the entry. */
    public void remember(@Nullable String identifier, @Nullable List<long[]> list) {
        var key = key(identifier);
        if (key == null)
            return;
        if (list == null || list.isEmpty()) {
            segments.remove(key);
            return;
        }
        if (segments.size() >= MAX_ENTRIES && !segments.containsKey(key))
            segments.clear();
        segments.put(key, List.copyOf(list));
    }

    /**
     * Pushes the "now playing" position past every segment (the client already dropped those seconds on the read side);
     * the timeline stays on **source time**, which is what the boombox stores as its {@code RingerPosition}.
     *
     * @return the original position (outside every segment, or nothing was reported), or the end of the segment it is in
     */
    public long positionAfterSkips(@Nullable String identifier, long positionMs) {
        var key = key(identifier);
        if (key == null || positionMs <= 0L)
            return positionMs;
        var list = segments.get(key);
        if (list == null || list.isEmpty())
            return positionMs;
        long pos = positionMs;
        // The segments are ascending by start; push past them one by one (the loop is capped by the table size so an overlap cannot spin forever)
        for (int guard = 0; guard <= list.size(); guard++) {
            long next = pos;
            for (var seg : list)
                if (seg != null && seg.length >= 2 && pos >= seg[0] && pos < seg[1])
                    next = Math.max(next, seg[1]);
            if (next == pos)
                return pos;
            pos = next;
        }
        return pos;
    }

    /**
     * Normalises to {@code bvid-pN}: the part is optional in a client identifier ({@code BV…} = part 1) while a ringer
     * playing part N uses {@code BV…-pN}, and both must land on the same key.
     */
    @Nullable
    private static String key(@Nullable String identifier) {
        if (identifier == null || identifier.isBlank())
            return null;
        var bare = BiliBiliIds.stripPage(identifier);
        if (bare == null || bare.isBlank())
            return null;
        return bare + "-p" + Math.max(1, BiliBiliIds.pageOf(identifier));
    }
}
