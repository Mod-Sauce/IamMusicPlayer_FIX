package dev.felnull.imp.client.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.music.resource.Lyric;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class LyricCacheManager {

    private LyricCacheManager() {}

    private static Path CACHE_DIR = AudioCacheManager.getBaseDir().resolve("lyric");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LogManager.getLogger(LyricCacheManager.class);

    private static final ExecutorService EXECUTOR =
            Executors.newFixedThreadPool(1);
    private static final ConcurrentHashMap<String, Future<?>> TASKS = new ConcurrentHashMap<>();

    public static void setCacheDir(Path path) {
        CACHE_DIR = path;
        try {
            Files.createDirectories(CACHE_DIR);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static {
        try {
            Files.createDirectories(CACHE_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Failed to init lyric cache dir", e);
        }
    }

    public static void cacheAsync(String id, Lyric lyric) {
        if(!IamMusicPlayer.getConfig().enableCache)return;
        if (lyric == null || lyric.isEmpty()) return;
        if (TASKS.containsKey(id)) return;
        Future<?> future = EXECUTOR.submit(() -> {
            try {
                Path file = CACHE_DIR.resolve(safeId(id) + ".json");

                Files.createDirectories(file.getParent());

                try (Writer w = Files.newBufferedWriter(file,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {

                    GSON.toJson(Lyric.toDTO(lyric), w);
                }

            } catch (Exception e) {
                LOGGER.error("[LyricCache] cache failed: {}", id, e);
            } finally {
                TASKS.remove(id);
            }
        });
        TASKS.put(id, future);
    }

    public static Lyric get(String id) {
        Path file = CACHE_DIR.resolve(safeId(id) + ".json");
        if (!Files.exists(file)) return null;
        try (Reader r = Files.newBufferedReader(file)) {
            return Lyric.fromDTO(GSON.fromJson(r, LyricDTO.class));
        } catch (Exception e) {
            LOGGER.error("[LyricCache] load failed: {}", id, e);
            return null;
        }
    }

    public static boolean has(String id) {
        if(!IamMusicPlayer.getConfig().enableCache)return false;
        Path file = CACHE_DIR.resolve(safeId(id) + ".json");
        return Files.exists(file);
    }

    private static String safeId(String id) {
        return id.replaceAll("[^a-zA-Z0-9-_]", "_");
    }
}