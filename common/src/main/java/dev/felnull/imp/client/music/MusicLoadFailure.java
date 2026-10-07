package dev.felnull.imp.client.music;

import org.jetbrains.annotations.Nullable;

/**
 * [client-side notebook] Which source failed to load (single slot: a boombox plays one source at a time); a cancel does
 * not count. Stopping is server-side in {@code MusicRing} — a client tick does not run while the UI is off that page.
 */
public final class MusicLoadFailure {
    private static String identifier;
    private static String loaderType;
    private static int count;
    private static boolean stopSent;
    /** Concrete failure reason per identifier (the platform's own words); a map, as another source may be probed at once. */
    private static final java.util.Map<String, String> reasons = new java.util.HashMap<>();
    /** Reason table cap: it grows as tracks are changed, and the whole table is cleared when exceeded (hints only). */
    private static final int REASON_LIMIT = 32;

    private MusicLoadFailure() {
    }

    /** Record one load failure (the count accumulates for the same identifier; a different identifier restarts at 1). */
    public static synchronized void report(@Nullable String lt, @Nullable String id) {
        if (id == null || id.isEmpty())
            return;
        if (id.equals(identifier)) {
            count++;
        } else {
            identifier = id;
            loaderType = lt;
            count = 1;
            stopSent = false;
        }
    }

    /**
     * Records the concrete reason "why it cannot play this time" (the platform's own words win). Calling it at the URL-resolving step, before the actual failure, is enough; it pairs with {@link #report}.
     */
    public static synchronized void noteReason(@Nullable String id, @Nullable String reason) {
        if (id == null || id.isEmpty() || reason == null)
            return;
        var r = reason.trim();
        if (r.isEmpty())
            return;
        if (reasons.size() >= REASON_LIMIT && !reasons.containsKey(id))
            reasons.clear();
        reasons.put(id, r);
    }

    /**
     * Same as above, but **only when there is no reason yet**.
     */
    public static synchronized void noteReasonIfAbsent(@Nullable String id, @Nullable String reason) {
        if (reason(id) != null)
            return;
        noteReason(id, reason);
    }

    /** The **concrete** failure reason of this identifier; null when there is none (nobody reported) — only then does the caller fall back to the generic hint. */
    @Nullable
    public static synchronized String reason(@Nullable String id) {
        if (id == null || id.isEmpty())
            return null;
        var r = reasons.get(id);
        if (r != null)
            return r;
        // [Fallback] The key may carry a source prefix (`kugou:<hash>`, `qq_music:<id>` from LavaPlayer's AudioReference) while callers look up the bare identifier,
        // so a suffix match keeps a prefixed key from silently degrading to the generic hint.
        var suffix = ":" + id;
        for (var e : reasons.entrySet())
            if (e.getKey().endsWith(suffix))
                return e.getValue();
        return null;
    }

    /** This source is fine now (loaded successfully / cancelled normally) — clear the record, and the hint disappears with it. */
    public static synchronized void clear(@Nullable String id) {
        if (id != null && id.equals(identifier)) {
            identifier = null;
            loaderType = null;
            count = 0;
            stopSent = false;
        }
        if (id != null)
            reasons.remove(id);
    }

    /** Whether the source currently playing failed to load (the UI draws the red hint from this). */
    public static synchronized boolean isFailed(@Nullable String id) {
        return id != null && !id.isEmpty() && id.equals(identifier) && count > 0;
    }

    /** Which source failed (`qq_music` / `kugou` / …). */
    @Nullable
    public static synchronized String failedLoaderType() {
        return loaderType;
    }
}
