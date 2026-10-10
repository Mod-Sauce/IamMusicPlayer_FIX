package dev.felnull.imp.client.bilibili;

/**
 * Request pacer: hands out request start slots {@code gapMs} apart, so N workers keep a known requests-per-second
 * without bursting (Bilibili's risk control counts bursts). Shared by the UP-space load and the part expansion.
 */
public final class BiliBiliPacer {
    private long next;

    /** The wait in milliseconds this caller must observe before starting its request ({@code 0} = start now). */
    public synchronized long reserve(long gapMs) {
        long now = System.currentTimeMillis();
        long slot = Math.max(now, next);
        next = slot + Math.max(0, gapMs);
        return slot - now;
    }

    public static void await(long waitMs) throws InterruptedException {
        if (waitMs > 0)
            Thread.sleep(waitMs);
    }
}
