package dev.felnull.imp.client.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.util.IMPPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AudioCacheManager {

    private AudioCacheManager() {}

    private static Path BASE_DIR = IMPPaths.getTmpFolder();
    private static Path AUDIO_DIR = BASE_DIR.resolve("audio");
    private static Path INDEX_FILE = BASE_DIR.resolve("index.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LogManager.getLogger(AudioCacheManager.class);

    private static final Map<String, CacheEntry> INDEX = new ConcurrentHashMap<>();

    private static final ExecutorService EXECUTOR =
            Executors.newFixedThreadPool(2);

    static {
        updatePath();
        try {
            Files.createDirectories(AUDIO_DIR);
            loadIndex();
        } catch (IOException e) {
            throw new RuntimeException("Failed to init cache", e);
        }
    }

    public static void updatePath() {
        if (IamMusicPlayer.getConfig().globalCache)
            BASE_DIR = IMPPaths.getUserCacheDir();
        else
            BASE_DIR = IMPPaths.getTmpFolder();

        if (IamMusicPlayer.getConfig().globalCache) {
            var oldDir = IMPPaths.getUserFolder().resolve("IamCache");
            if (oldDir.toFile().exists() && !BASE_DIR.toFile().exists()) {
                try {
                    Files.createDirectories(BASE_DIR.getParent());
                    Files.move(oldDir, BASE_DIR);
                } catch (IOException e) {
                    LOGGER.warn("Failed to migrate old cache dir", e);
                }
            }
        }

        try {
            Files.createDirectories(BASE_DIR);
        } catch (IOException e) {
            LOGGER.warn(e);
        }

        AUDIO_DIR = BASE_DIR.resolve("audio");
        INDEX_FILE = BASE_DIR.resolve("index.json");
        LyricCacheManager.setCacheDir(BASE_DIR.resolve("lyric"));
        INDEX.clear();
        try {
            loadIndex();
        } catch (IOException e) {
            throw new RuntimeException("Failed to init cache", e);
        }
    }

    public static Path getBaseDir() {
        return BASE_DIR;
    }

    public static void cacheAsync(String id, String url) {
        if(!IamMusicPlayer.getConfig().enableCache)return;
        if (!isValidUrl(url)) return;
        if (INDEX.containsKey(id)) return;

        EXECUTOR.submit(() -> {
            try {
                String fileName = UUID.randomUUID().toString();

                Path file = AUDIO_DIR.resolve(fileName);

                download(id, url, file);

                CacheEntry entry = new CacheEntry(id, fileName);

                INDEX.put(id, entry);
                saveIndex();

                LOGGER.debug("[AudioCache] cached: {}", id);

            } catch (Exception e) {
                LOGGER.error("[AudioCache] failed: {}", id, e);
            }
        });
    }

    public static Path getPath(String id) {
        CacheEntry entry = INDEX.get(id);
        if (entry == null) return null;

        Path path = AUDIO_DIR.resolve(entry.fileName);
        return Files.exists(path) ? path : null;
    }

    public static boolean has(String id){
        if(!IamMusicPlayer.getConfig().enableCache)return false;
        return INDEX.containsKey(id);
    }

    private static void download(String id, String urlStr, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        if(id.contains("bilibili")) {
            conn.setRequestProperty("Referer", "https://www.bilibili.com/");
            conn.setRequestProperty("Origin", "https://www.bilibili.com/");
        }

        try (InputStream in = conn.getInputStream();
             OutputStream out = Files.newOutputStream(target,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {

            byte[] buf = new byte[8192];
            int len;

            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }

        } finally {
            conn.disconnect();
        }
    }

    private static boolean isValidUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return scheme != null &&
                    (scheme.equals("http") || scheme.equals("https"));
        } catch (Exception e) {
            return false;
        }
    }

    private static void loadIndex() throws IOException {
        if (!Files.exists(INDEX_FILE)) return;

        try (Reader r = Files.newBufferedReader(INDEX_FILE)) {
            CacheEntry[] arr = GSON.fromJson(r, CacheEntry[].class);
            if (arr != null) {
                for (CacheEntry e : arr) {
                    INDEX.put(e.id, e);
                }
            }
        }
    }

    private static synchronized void saveIndex() {
        try (Writer w = Files.newBufferedWriter(INDEX_FILE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            GSON.toJson(INDEX.values(), w);

        } catch (IOException e) {
            LOGGER.error(e);
        }
    }

    private record CacheEntry(String id, String fileName) { }
}