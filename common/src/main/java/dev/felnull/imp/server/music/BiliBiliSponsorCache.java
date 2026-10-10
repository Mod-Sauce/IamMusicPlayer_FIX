package dev.felnull.imp.server.music;

import dev.felnull.imp.util.BiliBiliIds;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端知道的 B站 赞助/广告片段（客户端报的）：只用来把音响的位置**推到片段之后**；专用服务器不自己连 {@code bsbsb.top}；内存态即可。
 */
public final class BiliBiliSponsorCache {
    /** Cap on remembered tracks (a few hundred bytes each); a full wipe is safer than unbounded growth. */
    private static final int MAX_ENTRIES = 20000;

    private static final BiliBiliSponsorCache INSTANCE = new BiliBiliSponsorCache();

    /** 标识符（归一成 {@code bvid-pN}）→ 该曲要跳的片段（源时间毫秒，{@code [起,止]}）。 */
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
     * 把"正在播的位置"推到所有片段之后（客户端在读取端已把那几秒丢掉）；时间轴保持**视频时间**，音响存的 {@code RingerPosition} 就是它。
     *
     * @return 原位置（不在任何片段里 / 没人报过），或它所在片段的终点
     */
    public long positionAfterSkips(@Nullable String identifier, long positionMs) {
        var key = key(identifier);
        if (key == null || positionMs <= 0L)
            return positionMs;
        var list = segments.get(key);
        if (list == null || list.isEmpty())
            return positionMs;
        long pos = positionMs;
        // 片段按起点升序；一段一段往前推（循环上限 = 表长，防止表里有重叠时卡住）
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
     * 归一成 {@code bvid-pN}：客户端标识符里分P 是可选的（{@code BV…} = 第 1 P），ringer 放第 N P 用 {@code BV…-pN}，两边必须落到同一个键上。
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
