package dev.felnull.imp.client.music.player;

import java.util.List;

/** 读取端要跳过的片段（**源时间**毫秒）；{@link BaseMusicPlayer} 每读一批重新取一次，晚到也从当前读头起跳。
 * 时间轴分工：内部队列用"内容时间"，对外（歌词/HUD/进度/音响存的位置/服务端位置）一律"视频时间"。 */
public interface MusicSkipProvider {
    /** 一段要跳过的源时间区间。 */
    record Span(long startMs, long endMs) {
        public long lengthMs() {
            return Math.max(0L, endMs - startMs);
        }
    }

    /** 不跳。 */
    MusicSkipProvider NONE = new MusicSkipProvider() {
    };

    /** 本曲要丢的片段，按起点升序；空 = 不跳。 */
    default List<Span> skips() {
        return List.of();
    }

    /** Same list for the reader, which also passes where its **read head** is: a table arriving late may cover audio
     * already read (and played as-is), which must not count as skipped. */
    default List<Span> skips(long readHeadMs) {
        return skips();
    }

    /** 内容时间 → **视频时间**：跳段后两者差一个 Σ跳过。热路径会调它，实现必须走缓存、**不许阻塞**。 */
    default long sourcePositionOf(long contentMs) {
        return contentMs;
    }

    /** 视频时间 → 内容时间（开播时把调用方给的位置换算成内部队列的起点）。 */
    default long contentPositionOf(long sourceMs, boolean mayBlock) {
        return sourceMs;
    }

    /** 从视频位置 {@code sourceMs} 开始放时，真正该从哪个**源位置**开流（落在片段里就落到片段尾）。 */
    default long sourceReadStart(long sourceMs, boolean mayBlock) {
        return sourceMs;
    }
}
