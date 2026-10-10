package dev.felnull.imp.client.cache;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.felnull.imp.IamMusicPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** "UUID → player name" cache, a JSON object under the game directory; it must live under {@code
 * IamMusicPlayer.getConfig().IMPRFolder}, never {@code IMPPaths.getUserFolder()}. Trusted sources only (tab list, Otya
 * lookups, server-sent), not fallbacks like {@code uuid.toString()}; usercache.json is not read. Main thread only. */
public final class PlayerNameCache {
    private static final Logger LOGGER = LogManager.getLogger(PlayerNameCache.class);
    /** On-disk location: the mod folder under the **game directory** (`IMPRFolder` defaults to `iammusicplayerrenewed`).
     *  ⚠️ Do not use {@code IMPPaths.getUserFolder()} — that is `user.home` and would pollute the user home directory (see the class javadoc). */
    private static final Path FILE = Paths.get(IamMusicPlayer.getConfig().IMPRFolder).resolve("player_names.json");
    /** Upper bound; beyond it the oldest entry is dropped (it is only a cache, not data). */
    private static final int MAX_ENTRIES = 2000;
    /** Minimum interval between online-list refreshes. */
    private static final long REFRESH_INTERVAL_MS = 2000L;
    /** Save debounce: names accumulate slowly, so there is no need to write on every change. */
    private static final long SAVE_DEBOUNCE_MS = 10000L;

    /** Background save thread (daemon, single-threaded ⇒ naturally serial and ordered). */
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "iammusicplayer-namecache-writer");
        t.setDaemon(true);
        return t;
    });

    /** Insertion order = eviction order. Only read/written on the client main thread. */
    private static final Map<UUID, String> NAMES = new LinkedHashMap<>();
    private static boolean loaded;
    /** There are unsaved changes (volatile: the background writer must set it back to true on failure). */
    private static volatile boolean dirty;
    /** Whether a write is already in flight (avoids pile-up). */
    private static volatile boolean writing;
    private static long lastRefreshAt;
    private static long lastSaveAt;

    private PlayerNameCache() {
    }

    /** Look up the cache (triggers no network/lookup). Returns null when absent. */
    @Nullable
    public static String get(@NotNull UUID playerId) {
        loadIfNeeded();
        return NAMES.get(playerId);
    }

    /** Reverse lookup: name → UUID (case-insensitive; null when not found), the entry bound making the linear scan
     * acceptable; a playlist-cover face needs the UUID to ask for a skin instead of the offline default. */
    @Nullable
    public static UUID uuidByName(@NotNull String name) {
        var trimmed = name.trim();
        if (trimmed.isEmpty()) return null;
        loadIfNeeded();
        for (var entry : NAMES.entrySet()) {
            if (trimmed.equalsIgnoreCase(entry.getValue())) return entry.getKey();
        }
        return null;
    }

    /** Remember a **trusted** name (blank/null is ignored). */
    public static void remember(@NotNull UUID playerId, @Nullable String name) {
        if (name == null) return;
        var trimmed = name.trim();
        if (trimmed.isEmpty()) return;
        loadIfNeeded();
        if (trimmed.equals(NAMES.get(playerId))) return;
        NAMES.put(playerId, trimmed);
        while (NAMES.size() > MAX_ENTRIES) {
            var it = NAMES.keySet().iterator();
            if (!it.hasNext()) break;
            it.next();
            it.remove();
        }
        dirty = true;
    }

    /**
     * Called from the client tick (throttled internally, safe to call every tick).
     */
    public static void tick(@NotNull Minecraft mc) {
        var now = System.currentTimeMillis();
        if (now - lastRefreshAt >= REFRESH_INTERVAL_MS) {
            lastRefreshAt = now;
            refreshFromTabList(mc);
        }
        if (dirty && now - lastSaveAt >= SAVE_DEBOUNCE_MS)
            save();
    }

    /** Remember "who is currently online" (including yourself) into the cache. */
    public static void refreshFromTabList(@NotNull Minecraft mc) {
        var player = mc.player;
        if (player == null) return;
        remember(player.getGameProfile().getId(), player.getGameProfile().getName());
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return;
        for (var info : connection.getOnlinePlayers()) {
            var profile = info.getProfile();
            if (profile.getId() != null)
                remember(profile.getId(), profile.getName());
        }
    }

    private static void loadIfNeeded() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(FILE)) return;
            var text = Files.readString(FILE, StandardCharsets.UTF_8);
            var json = JsonParser.parseString(text);
            if (!json.isJsonObject()) return;
            JsonObject obj = json.getAsJsonObject();
            for (var entry : obj.entrySet()) {
                var id = parseUuid(entry.getKey());
                if (id == null) continue;
                var value = entry.getValue();
                if (value == null || !value.isJsonPrimitive()) continue;
                var name = value.getAsString();
                if (name == null || name.isBlank()) continue;
                NAMES.put(id, name.trim());
            }
        } catch (Exception e) {
            LOGGER.warn("[PlayerNameCache] Could not read the name cache, continuing with an empty one: {}", FILE, e);
        }
    }

    /** The main thread only takes a snapshot and hands it to the background writer; on failure the background sets
     * {@code dirty} back to true ⇒ retried on the next tick. {@link #NAMES} is always the authoritative data. */
    private static void save() {
        if (!dirty || writing) return;
        writing = true;
        dirty = false;
        lastSaveAt = System.currentTimeMillis();
        var snapshot = new LinkedHashMap<>(NAMES);
        try {
            WRITER.execute(() -> writeSnapshot(snapshot));
        } catch (Throwable t) {
            // Even the thread pool rejected it (should not happen): restore the state and try again later
            writing = false;
            dirty = true;
            LOGGER.warn("[PlayerNameCache] Could not submit the write task: {}", FILE, t);
        }
    }

    /** Background thread: write a temp file → atomic replace. On failure dirty is not cleared (the next tick retries). */
    private static void writeSnapshot(Map<UUID, String> snapshot) {
        try {
            var parent = FILE.getParent();
            if (parent != null) Files.createDirectories(parent);
            var obj = new JsonObject();
            snapshot.forEach((id, name) -> obj.addProperty(id.toString(), name));
            var tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, obj.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Some file systems do not support atomic moves ⇒ fall back to a plain replace (the content is still the complete temp file)
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.warn("[PlayerNameCache] Could not write the name cache (in-memory data is unaffected, retrying later): {}", FILE, e);
            dirty = true;
        } finally {
            writing = false;
        }
    }

    @Nullable
    private static UUID parseUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
