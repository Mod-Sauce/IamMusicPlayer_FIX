package dev.felnull.imp.client.gui;

import dev.felnull.imp.client.cache.PlayerNameCache;
import dev.felnull.imp.music.resource.AuthorityInfo;
import dev.felnull.imp.music.resource.MusicPlayList;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.client.util.OEClientUtils;

import java.util.UUID;

/**
 * The single source of "player name" in the UI: playlist owner → online (tab list) → local cache → the original chain → caller fallback; never vanilla usercache.json; the appearance path must use {@code getSelectedMusicPlayList(blockEntity)}.
 */
public final class PlayerNameUtil {
    private PlayerNameUtil() {
    }

    /** Playlist-context version ({@code playList} may be null). Returns the UUID string when no name is found. */
    @NotNull
    public static String playerName(@Nullable MusicPlayList playList, @NotNull UUID playerId) {
        return playerName(playList == null ? null : playList.getAuthority(), playerId);
    }

    /** Authority-data version ({@code authority} may be null). */
    @NotNull
    public static String playerName(@Nullable AuthorityInfo authority, @NotNull UUID playerId) {
        if (authority != null) {
            // Server-sent names have top priority (offline members and phone clients with no cache rely on it).
            var serverName = authority.getMemberName(playerId);
            if (serverName != null && !serverName.isBlank())
                return serverName;
            if (playerId.equals(authority.getOwner())) {
                var ownerName = authority.getOwnerName();
                if (ownerName != null && !ownerName.isBlank())
                    return ownerName;
            }
        }
        var known = onlineOrCached(playerId);
        return known != null ? known : engineName(playerId);
    }

    /** Without playlist context (music adder, player list, player picker widget, etc.). */
    @NotNull
    public static String playerName(@NotNull UUID playerId) {
        var known = onlineOrCached(playerId);
        return known != null ? known : engineName(playerId);
    }

    /** For list rows: falls back to the UUID string when {@code ifUnknown} is null (the music list passes "unknown player"). */
    @NotNull
    public static Component playerNameComponent(@Nullable MusicPlayList playList, @NotNull UUID playerId, @Nullable Component ifUnknown) {
        if (ifUnknown == null)
            return Component.literal(playerName(playList, playerId));
        var known = cachedFor(playList, playerId);
        return known != null ? Component.literal(known) : ifUnknown;
    }

    /** For list rows (no context version, fallback = UUID string). */
    @NotNull
    public static Component playerNameComponent(@Nullable MusicPlayList playList, @NotNull UUID playerId) {
        return Component.literal(playerName(playList, playerId));
    }

    /** For list rows (no playlist context, fallback = UUID string). */
    @NotNull
    public static Component playerNameComponent(@NotNull UUID playerId) {
        return Component.literal(playerName(playerId));
    }

    /** Same sources as {@link #playerName}, but does **not** trigger the original lookup chain (no blocking): returns null when nothing is found. */
    @Nullable
    private static String cachedFor(@Nullable MusicPlayList playList, @NotNull UUID playerId) {
        if (playList != null) {
            var authority = playList.getAuthority();
            if (authority != null) {
                var serverName = authority.getMemberName(playerId);
                if (serverName != null && !serverName.isBlank())
                    return serverName;
                if (playerId.equals(authority.getOwner())) {
                    var ownerName = authority.getOwnerName();
                    if (ownerName != null && !ownerName.isBlank())
                        return ownerName;
                }
            }
        }
        return onlineOrCached(playerId);
    }

    /**
     * Server-sent names take top priority — the only option for phone clients that have no cache and cannot reach the skin sites.
     */
    @NotNull
    public static String playerName(@Nullable String serverName, @NotNull UUID playerId) {
        if (serverName != null && !serverName.isBlank())
            return serverName;
        return playerName(playerId);
    }

    /** Component form of the version above (the name carries "server" to avoid ambiguity with the existing overload when null is passed). */
    @NotNull
    public static Component serverNameComponent(@Nullable String serverName, @NotNull UUID playerId, @Nullable Component ifUnknown) {
        if (serverName != null && !serverName.isBlank())
            return Component.literal(serverName);
        return playerNameComponent(null, playerId, ifUnknown);
    }

    /** Server-sent name clamped to maxWidth (falls back to the name computed by the existing chain). Used for fixed-width drawing of music rows. */
    @NotNull
    public static String serverNameOrFallback(@Nullable String serverName, @NotNull UUID playerId) {
        return playerName(serverName, playerId);
    }

    /**
     * Reverse, name → UUID (online first, then the local cache): a playlist cover stores a name while the face chain needs a UUID; never the original chain, which this render path may hit every frame.
     */
    @Nullable
    public static UUID uuidByName(@NotNull String name) {
        var trimmed = name.trim();
        if (trimmed.isEmpty()) return null;
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            var connection = mc.getConnection();
            if (connection != null) {
                for (var info : connection.getOnlinePlayers()) {
                    var profile = info.getProfile();
                    var onlineName = profile.getName();
                    if (onlineName != null && trimmed.equalsIgnoreCase(onlineName)) {
                        PlayerNameCache.remember(profile.getId(), onlineName);
                        return profile.getId();
                    }
                }
            }
        }
        return PlayerNameCache.uuidByName(trimmed);
    }

    /**
     * Only the online / local cache name, never the original lookup chain — that chain does synchronous HTTP when offline and must not run on the render thread.
     */
    @Nullable
    public static String cachedName(@NotNull UUID playerId) {
        return onlineOrCached(playerId);
    }

    /**
     * Online (tab list / self, remembered into the cache on the way) then the local cache; online wins — the cache may be stale after a rename.
     */
    @Nullable
    private static String onlineOrCached(@NotNull UUID playerId) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player != null) {
            if (playerId.equals(player.getGameProfile().getId())) {
                var selfName = player.getGameProfile().getName();
                PlayerNameCache.remember(playerId, selfName);
                if (selfName != null && !selfName.isBlank()) return selfName;
            }
            var connection = mc.getConnection();
            if (connection != null) {
                var info = connection.getPlayerInfo(playerId);
                if (info != null) {
                    var name = info.getProfile().getName();
                    if (name != null && !name.isBlank()) {
                        PlayerNameCache.remember(playerId, name);
                        return name;
                    }
                }
            }
        }
        return PlayerNameCache.get(playerId);
    }

    /** Original lookup chain (Otya: tab list → fallback synchronous HTTP to Mojang). Remember it into the cache when found, so it is still known when offline later. */
    @NotNull
    private static String engineName(@NotNull UUID playerId) {
        var found = OEClientUtils.getPlayerNameByUUID(playerId);
        found.ifPresent(name -> PlayerNameCache.remember(playerId, name));
        return found.orElseGet(playerId::toString);
    }
}
