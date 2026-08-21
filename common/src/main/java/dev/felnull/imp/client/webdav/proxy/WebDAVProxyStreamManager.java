package dev.felnull.imp.client.webdav.proxy;

import dev.felnull.imp.client.cache.AudioCacheManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class WebDAVProxyStreamManager {
    private static final Logger LOGGER = LogManager.getLogger(WebDAVProxyStreamManager.class);
    private static final Map<UUID, DownloadState> DOWNLOADS = new ConcurrentHashMap<>();

    private WebDAVProxyStreamManager() {
    }

    public static void begin(UUID sessionId, long totalSize, String relativePath) {
        LOGGER.info("[WebDAVProxy] begin session={} totalSize={} relativePath='{}'", sessionId, totalSize, relativePath);
        DOWNLOADS.compute(sessionId, (id, existing) -> {
            if (existing == null) return new DownloadState(totalSize, relativePath);
            existing.totalSize = totalSize;
            existing.relativePath = relativePath;
            return existing;
        });
    }

    public static void append(UUID sessionId, byte[] chunk) {
        DownloadState state = DOWNLOADS.computeIfAbsent(sessionId, id -> new DownloadState(-1, ""));
        LOGGER.info("[WebDAVProxy] append session={} chunkSize={}", sessionId, chunk.length);
        synchronized (state) {
            try {
                state.buffer.write(chunk);
            } catch (IOException e) {
                state.error = e;
                state.latch.countDown();
            }
        }
    }

    public static void finish(UUID sessionId) {
        DownloadState state = DOWNLOADS.get(sessionId);
        if (state == null) return;
        synchronized (state) {
            try {
                Path dir = AudioCacheManager.getBaseDir().resolve("webdav_proxy");
                Files.createDirectories(dir);
                String ext = getExtension(state.relativePath);
                Path file = dir.resolve(sessionId + ext);
                Files.write(file, state.buffer.toByteArray());
                state.file = file;
                LOGGER.info("[WebDAVProxy] finish session={} wroteFile={} bytes={}", sessionId, file, state.buffer.size());
            } catch (IOException e) {
                state.error = e;
            } finally {
                state.latch.countDown();
            }
        }
    }

    public static void fail(UUID sessionId, String message) {
        LOGGER.error("[WebDAVProxy] fail session={} message='{}'", sessionId, message);
        DownloadState state = DOWNLOADS.computeIfAbsent(sessionId, id -> new DownloadState(-1, ""));
        state.error = new IOException(message);
        state.latch.countDown();
    }

    public static Path await(UUID sessionId) throws Exception {
        DownloadState state = DOWNLOADS.computeIfAbsent(sessionId, id -> new DownloadState(-1, ""));
        LOGGER.info("[WebDAVProxy] await session={}", sessionId);
        if (!state.latch.await(60, TimeUnit.SECONDS)) {
            throw new IOException("Timed out waiting for WebDAV proxy stream");
        }
        if (state.error != null) throw state.error;
        if (state.file == null) throw new IOException("WebDAV proxy stream produced no file");
        return state.file;
    }

    private static String getExtension(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return ".bin";
        int slash = relativePath.lastIndexOf('/');
        String name = slash >= 0 ? relativePath.substring(slash + 1) : relativePath;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return ".bin";
        return name.substring(dot);
    }

    private static final class DownloadState {
        private volatile long totalSize;
        private volatile String relativePath;
        private final CountDownLatch latch = new CountDownLatch(1);
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private volatile Path file;
        private volatile IOException error;

        private DownloadState(long totalSize, String relativePath) {
            this.totalSize = totalSize;
            this.relativePath = relativePath;
        }
    }
}
