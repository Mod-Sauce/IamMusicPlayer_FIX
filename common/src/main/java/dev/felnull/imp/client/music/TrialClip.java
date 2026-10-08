package dev.felnull.imp.client.music;

import dev.felnull.imp.music.resource.MusicSource;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "The address this source resolved to is only a **trial clip**" — written by whoever sees it in the platform's own answer, read by the loaders; never judged by the decoded length, which is unknown for a remote track.
 */
public final class TrialClip {
    private TrialClip() {
    }

    /** {@code <loaderType>|<identifier>} — both sides build the key the same way. */
    private static final Map<String, long[]> CLIPS = new ConcurrentHashMap<>();

    /** Records "the address for this key is {@code clipMs} long" (0 = the platform flagged a trial without giving a length). */
    public static void remember(String loaderType, @Nullable String identifier, long clipMs) {
        if (identifier == null || identifier.isEmpty()) return;
        CLIPS.put(key(loaderType, identifier), new long[]{clipMs});
    }

    /** Drops the record (the source played in full after all, or the entry is gone). */
    public static void forget(String loaderType, @Nullable String identifier) {
        if (identifier == null || identifier.isEmpty()) return;
        CLIPS.remove(key(loaderType, identifier));
    }

    /** The clip length the platform reported, or 0 when it reported a trial without a length; -1 when nothing is known. */
    public static long clipMs(MusicSource source) {
        long[] v = CLIPS.get(key(source.getLoaderType(), source.getIdentifier()));
        return v == null ? -1 : v[0];
    }

    /**
     * True when the platform's own answer says this address is a trial: it gave a length and that length is a fraction of the declared one, so a genuinely short track can never be taken for one.
     */
    public static boolean isTrialAddress(MusicSource source) {
        long clip = clipMs(source);
        if (clip < 0) return false;
        if (clip == 0) return true;                       // flagged as a trial, length not given
        long want = source.getDuration();
        return want >= 60_000 && clip <= 61_000 && want - clip >= 30_000;
    }

    private static String key(String loaderType, String identifier) {
        return loaderType + "|" + identifier;
    }
}
