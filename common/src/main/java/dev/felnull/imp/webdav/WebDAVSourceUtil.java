package dev.felnull.imp.webdav;

import dev.felnull.imp.music.resource.MusicSource;

import java.util.UUID;

public final class WebDAVSourceUtil {
    public static final String LOADER_TYPE = "webdav";
    public static final String PROXY_LOADER_TYPE = "webdav_proxy";

    private WebDAVSourceUtil() {
    }

    public static String encodeOwnedPath(UUID owner, String relativePath) {
        return owner + "|" + relativePath;
    }

    public static UUID extractOwner(String identifier) {
        if (identifier == null) return null;
        int idx = identifier.indexOf('|');
        if (idx <= 0) return null;
        try {
            return UUID.fromString(identifier.substring(0, idx));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static String extractPath(String identifier) {
        if (identifier == null || identifier.isBlank()) return "";
        int idx = identifier.indexOf('|');
        if (idx < 0 || idx + 1 >= identifier.length()) return identifier;
        return identifier.substring(idx + 1);
    }

    public static boolean isOwnedIdentifier(String identifier) {
        return extractOwner(identifier) != null;
    }

    public static boolean isWebDAV(MusicSource source) {
        return source != null && LOADER_TYPE.equals(source.getLoaderType());
    }

    public static boolean isProxy(MusicSource source) {
        return source != null && PROXY_LOADER_TYPE.equals(source.getLoaderType());
    }
}
