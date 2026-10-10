package dev.felnull.imp.server.music;

import dev.felnull.imp.util.BiliBiliIds;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * B站 投稿时间，客户端报上来的（服务端自己没法查，见 {@code BiliBiliPartCache}）。只在内存：没报过的曲子按 {@code 0} 参与排序，下次进页面再报一次。
 */
public final class BiliBiliPubdateCache {
    /** 上限（两万条 ≈ 几百 KB），满了整体清空，粗暴但不会无限涨。 */
    private static final int MAX_ENTRIES = 20000;
    private static final BiliBiliPubdateCache INSTANCE = new BiliBiliPubdateCache();

    private final Map<String, Long> pubdates = new ConcurrentHashMap<>();

    private BiliBiliPubdateCache() {
    }

    public static BiliBiliPubdateCache getInstance() {
        return INSTANCE;
    }

    /** 毫秒时间戳；{@code <= 0} 视为"没报"直接丢掉。 */
    public void remember(String bvid, long pubdateMs) {
        var bare = BiliBiliIds.stripPage(bvid);
        if (bare == null || bare.isBlank() || pubdateMs <= 0L)
            return;
        if (pubdates.size() >= MAX_ENTRIES && !pubdates.containsKey(bare))
            pubdates.clear();
        pubdates.put(bare, pubdateMs);
    }

    /** {@code 0} = 没报过（按 bvid 查，分P 后缀会被剥掉）。 */
    public long pubdateOf(@Nullable String identifier) {
        var bare = BiliBiliIds.stripPage(identifier);
        if (bare == null || bare.isBlank())
            return 0L;
        var v = pubdates.get(bare);
        return v == null ? 0L : v;
    }
}
