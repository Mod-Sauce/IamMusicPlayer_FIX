package dev.felnull.imp.server.music;

import dev.felnull.imp.util.BiliBiliIds;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bilibili upload dates reported by a client (the server cannot look them up itself, see {@code BiliBiliPartCache}).
 * In-memory only: a song that was never reported sorts as {@code 0} and is reported again the next time the page opens.
 */
public final class BiliBiliPubdateCache {
    /** Cap (20 000 entries ≈ a few hundred KB); a full wipe past it is crude but cannot grow without bound. */
    private static final int MAX_ENTRIES = 20000;
    private static final BiliBiliPubdateCache INSTANCE = new BiliBiliPubdateCache();

    private final Map<String, Long> pubdates = new ConcurrentHashMap<>();

    private BiliBiliPubdateCache() {
    }

    public static BiliBiliPubdateCache getInstance() {
        return INSTANCE;
    }

    /** Millisecond timestamp; {@code <= 0} counts as "not reported" and is dropped. */
    public void remember(String bvid, long pubdateMs) {
        var bare = BiliBiliIds.stripPage(bvid);
        if (bare == null || bare.isBlank() || pubdateMs <= 0L)
            return;
        if (pubdates.size() >= MAX_ENTRIES && !pubdates.containsKey(bare))
            pubdates.clear();
        pubdates.put(bare, pubdateMs);
    }

    /** {@code 0} = never reported (looked up by bvid, with the part suffix stripped). */
    public long pubdateOf(@Nullable String identifier) {
        var bare = BiliBiliIds.stripPage(identifier);
        if (bare == null || bare.isBlank())
            return 0L;
        var v = pubdates.get(bare);
        return v == null ? 0L : v;
    }
}
