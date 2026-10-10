package dev.felnull.imp.client.music.link;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Diagnostic logging for the link parsing/loading chain. Off by default (no output) unless enabled with the JVM argument
 * {@code -Diammusicplayer.debug.link=true} or the environment variable {@code IMP_LINK_DEBUG=1}; enabled logs go through
 * the log4j {@code ImMusicPlayer-Link} logger (game log and session log file).
 */
public final class LinkDebug {
    private static final Logger LOGGER = LogManager.getLogger("ImMusicPlayer-Link");
    private static final boolean ENABLED = resolveEnabled();

    private LinkDebug() {
    }

    private static boolean resolveEnabled() {
        try {
            var prop = System.getProperty("iammusicplayer.debug.link");
            if (prop != null)
                return "true".equalsIgnoreCase(prop) || "1".equals(prop);
            var env = System.getenv("IMP_LINK_DEBUG");
            return env != null && ("1".equals(env) || "true".equalsIgnoreCase(env));
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    /**
     * Compresses a credential-bearing address into a form safe to log: {@code scheme://host} plus the first path
     * segment (none when there is only one — that one is the token); {@code "(unparsable)"} if it cannot be parsed.
     */
    @NotNull
    public static String safeUrl(@Nullable String url) {
        if (url == null || url.isEmpty())
            return "null";
        try {
            var u = java.net.URI.create(url);
            var scheme = u.getScheme();
            var host = u.getHost();
            if (scheme == null || host == null)
                return "(unparsable)";
            var path = u.getPath();
            var head = "";
            if (path != null && path.length() > 1) {
                var end = path.indexOf('/', 1);
                // ⚠️ A single-segment path *is* the credential itself (e.g. `https://host/<token>`), so drop it entirely;
                // only multi-segment paths keep their first segment (kugou is `/<time>/<token>/v3/...`).
                head = end < 0 ? "" : path.substring(0, end + 1);
            }
            return scheme + "://" + host + head + "…";
        } catch (Exception e) {
            return "(unparsable)";
        }
    }

    /**
     * Log form of an **identifier string** (better suited to {@code MusicSource#getIdentifier()} than {@link #safeUrl}).
     */
    @NotNull
    public static String safeIdentifier(@Nullable String id) {
        if (id == null || id.isEmpty())
            return "null";
        if (id.contains("://"))
            return safeUrl(id);
        return id.length() > 120 ? id.substring(0, 120) + "..." : id;
    }

    public static void log(@NotNull String message) {
        if (!ENABLED)
            return;
        LOGGER.info("[IMPLink] {}", message);
    }

    /**
     * Join a series of "key=value" pairs into one log line (newlines in values are flattened to avoid flooding the log).
     */
    public static void log(@NotNull String stage, @NotNull Object... keyValues) {
        if (!ENABLED)
            return;

        var sb = new StringBuilder(stage);
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            sb.append(" | ").append(keyValues[i]).append('=');
            var v = keyValues[i + 1];
            var s = String.valueOf(v);
            if (s.length() > 200)
                s = s.substring(0, 200) + "...";
            sb.append(s.replace('\n', ' ').replace('\r', ' '));
        }
        LOGGER.info("[IMPLink] {}", sb);
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, long[]> THROTTLE =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** Throttling for the same key: at most one line per 1000ms, at most 40 lines in total. */
    private static final long THROTTLE_MS = 1000L;
    private static final int THROTTLE_MAX_LINES = 40;

    /** [Throttled logging] Ask "is this line worth logging now" first, and only then fetch what to log. */
    public static boolean shouldLogThrottled(@NotNull String key) {
        if (!ENABLED)
            return false;

        var st = THROTTLE.computeIfAbsent(key, k -> new long[2]);
        synchronized (st) {
            if (st[0] >= THROTTLE_MAX_LINES)
                return false;
            long now = System.currentTimeMillis();
            if (now - st[1] < THROTTLE_MS)
                return false;
            st[0]++;
            st[1] = now;
            return true;
        }
    }
}
