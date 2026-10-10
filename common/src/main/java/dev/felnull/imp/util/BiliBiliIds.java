package dev.felnull.imp.util;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * The Bilibili identifier conventions **both sides** need: a video is its bare BV number, a part is that plus {@code -p<N>}
 * ({@code BV1SP411V7mW-p2}). In {@code util} because server code (part continuation, the ringer) must follow them too.
 */
public final class BiliBiliIds {
    /** Part suffix {@code -p<N>} (N≥1, at the end of the identifier string). */
    private static final Pattern PAGE_SUFFIX = Pattern.compile("-p(\\d+)$");

    private BiliBiliIds() {
    }

    /** The part number of an identifier; {@code 1} when there is no suffix (an out-of-range part falls back to part 1 where the audio is fetched). */
    public static int pageOf(@Nullable String identifier) {
        if (identifier == null)
            return 1;
        var m = PAGE_SUFFIX.matcher(identifier.strip());
        if (!m.find())
            return 1;
        try {
            return Math.max(1, Integer.parseInt(m.group(1)));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** The bare video number of an identifier (no part suffix). */
    @Nullable
    public static String stripPage(@Nullable String identifier) {
        return identifier == null ? null : PAGE_SUFFIX.matcher(identifier.strip()).replaceAll("");
    }

    /** The identifier of one part of a video: {@code page <= 1} keeps the bare number (part 1 is the plain form). */
    public static String withPage(@Nullable String bvid, int page) {
        var bare = stripPage(bvid);
        if (bare == null)
            return "";
        return page <= 1 ? bare : bare + "-p" + page;
    }
}
