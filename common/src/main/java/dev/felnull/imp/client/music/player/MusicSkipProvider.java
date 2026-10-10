package dev.felnull.imp.client.music.player;

import java.util.List;

/** The segments the read side skips (**source-time** milliseconds); {@link BaseMusicPlayer} asks again for every batch,
 * and a late answer skips from the current read head. Timeline split: the internal queue runs on content time, while
 * everything reported (lyrics, HUD, progress, the position the boombox stores, the server position) is source time. */
public interface MusicSkipProvider {
    /** One source-time span to skip. */
    record Span(long startMs, long endMs) {
        public long lengthMs() {
            return Math.max(0L, endMs - startMs);
        }
    }

    /** Skip nothing. */
    MusicSkipProvider NONE = new MusicSkipProvider() {
    };

    /** The segments to drop for this track, ascending by start; empty = skip nothing. */
    default List<Span> skips() {
        return List.of();
    }

    /** Same list for the reader, which also passes where its **read head** is: a table arriving late may cover audio
     * already read (and played as-is), which must not count as skipped. */
    default List<Span> skips(long readHeadMs) {
        return skips();
    }

    /** Content time → **source time**: after a skip the two differ by the sum skipped. This is a hot path, so an implementation must use its cache and **must not block**. */
    default long sourcePositionOf(long contentMs) {
        return contentMs;
    }

    /** Source time → content time (converts the caller's position into the queue's starting point when playback starts). */
    default long contentPositionOf(long sourceMs, boolean mayBlock) {
        return sourceMs;
    }

    /** When playback starts at source position {@code sourceMs}, the **source position** to open the stream at (inside a segment it lands on the segment's end). */
    default long sourceReadStart(long sourceMs, boolean mayBlock) {
        return sourceMs;
    }
}
