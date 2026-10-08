package dev.felnull.imp.client.music;

import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicPlayList;
import dev.felnull.imp.networking.IMPPackets;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MusicSyncManager {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final MusicSyncManager INSTANCE = new MusicSyncManager();
    public List<MusicPlayList> myPlayList;
    private long myPlayListLastUpdateTime;
    public PlayListInfo myPlayListInfo;
    public List<MusicPlayList> canJoinPlayList;
    private long canJoinPlayListLastUpdateTime;
    public PlayListInfo canJoinPlayListInfo;
    /** Playlists I was invited to (not accepted yet) (the server filters by INVITATION entries, see {@code PlayListGetType.INVITES}). */
    public List<MusicPlayList> invitePlayList;
    private long invitePlayListLastUpdateTime;
    public final Map<UUID, List<Music>> musics = new HashMap<>();
    private final Map<UUID, Long> musicUpdateTimes = new HashMap<>();
    /** How many songs a playlist holds (server-sent with a page) while its list is still being paged in. */
    private final Map<UUID, Integer> musicTotals = new HashMap<>();
    /** Playlists whose song list is not complete yet (the server sends it page by page, see {@link #applyMusicPage}). */
    private final java.util.Set<UUID> musicLoading = new java.util.HashSet<>();
    /** Pages received per playlist — only for the "songs synced" log line. */
    private final Map<UUID, Integer> musicPages = new HashMap<>();

    public static MusicSyncManager getInstance() {
        return INSTANCE;
    }

    @Nullable
    public List<Music> getMusics(@Nullable UUID playList) {
        if (playList == null) return null;
        long time = 0;
        if (musicUpdateTimes.containsKey(playList)) {
            time = musicUpdateTimes.get(playList);
        } else {
            musicUpdateTimes.put(playList, 0L);
        }
        if (System.currentTimeMillis() - time >= 1000 * 60) {
            sendRequest(IMPPackets.MusicSyncType.MUSIC_BY_PLAYLIST, playList, 0);
            musicUpdateTimes.put(playList, System.currentTimeMillis());
        }
        var cached = musics.get(playList);
        return cached == null ? null : java.util.Collections.unmodifiableList(cached);
    }

    /** One page of a playlist's songs: the first page replaces the cache, later ones append, and while the list is
     * shorter than the total the next page is fetched at once (one packet NBT tag is capped at 2 MiB, ~1100 songs). */
    public void applyMusicPage(@Nullable UUID playList, List<Music> page, int offset, int total) {
        if (playList == null) return;
        List<Music> list;
        if (offset <= 0) {
            list = new ArrayList<>(page);
            musics.put(playList, list);
        } else {
            var existing = musics.get(playList);
            if (existing == null) {
                list = new ArrayList<>(page);
                musics.put(playList, list);
            } else {
                list = existing;
                // A page may arrive twice (an overlapping re-request): drop what we already have behind this offset.
                if (offset < list.size())
                    list.subList(offset, list.size()).clear();
                list.addAll(page);
            }
        }
        musicTotals.put(playList, total);
        // The counter is per **sync**: the page re-requests the whole list when the 60 s cache is stale (and after our
        // own writes), so accumulating across syncs would report "8 pages" for a two-page playlist.
        if (offset <= 0)
            musicPages.put(playList, 1);
        else
            musicPages.merge(playList, 1, Integer::sum);
        if (total > list.size()) {
            musicLoading.add(playList);
            sendRequest(IMPPackets.MusicSyncType.MUSIC_BY_PLAYLIST, playList, list.size());
        } else {
            musicLoading.remove(playList);
            musicUpdateTimes.put(playList, System.currentTimeMillis());
            dev.felnull.imp.client.music.link.LinkDebug.log("playlist songs synced", "playlist", playList,
                    "songs", list.size(), "pages", musicPages.getOrDefault(playList, 1));
        }
    }

    /** True while this playlist's songs are still arriving page by page (the list on screen is not complete yet). */
    public boolean isMusicLoading(@Nullable UUID playList) {
        return playList != null && musicLoading.contains(playList);
    }

    /** How many songs the server said the playlist has; 0 when nothing was synced yet. */
    public int getMusicTotal(@Nullable UUID playList) {
        return playList == null ? 0 : musicTotals.getOrDefault(playList, 0);
    }

    /** Forgets when {@code playList}'s songs were last synced so the next {@link #getMusics(UUID)} asks the server
     * again: it never pushes after an add, it only answers {@code MUSIC_SYNC_CTS}, and the cache re-requests only once
     * 60 s old. The cached list is deliberately kept, so the old rows stay on screen until the answer lands. */
    public void invalidateMusics(@Nullable UUID playList) {
        if (playList == null) return;
        musicUpdateTimes.put(playList, 0L);
    }

    /** Forgets when the playlist list was last synced so the next {@link #getMyPlayList()} asks again (same trap as
     * {@link #invalidateMusics}: else the client keeps the object it has for up to 60 s). The invited list is dropped
     * too — the page shows both and an invite can change in the same window. */
    public void invalidatePlayLists() {
        myPlayListLastUpdateTime = 0L;
        invitePlayListLastUpdateTime = 0L;
    }

    public List<MusicPlayList> getMyPlayList() {
        if (System.currentTimeMillis() - myPlayListLastUpdateTime >= 1000 * 60) {
            sendRequest(IMPPackets.MusicSyncType.PLAYLIST_MY_LIST, mc.player.getGameProfile().getId());
            myPlayListLastUpdateTime = System.currentTimeMillis();
        }
        return myPlayList;
    }

    public PlayListInfo getMyPlayListInfo() {
        return myPlayListInfo;
    }

    public List<MusicPlayList> getCanJoinPlayList() {
        if (System.currentTimeMillis() - canJoinPlayListLastUpdateTime >= 1000 * 60) {
            sendRequest(IMPPackets.MusicSyncType.PLAYLIST_CAN_JOIN, mc.player.getGameProfile().getId());
            canJoinPlayListLastUpdateTime = System.currentTimeMillis();
        }
        return canJoinPlayList;
    }

    public PlayListInfo getCanJoinPlayListInfo() {
        return canJoinPlayListInfo;
    }

    /**
     * Playlists I was invited to but have not accepted yet.
     */
    public List<MusicPlayList> getInvitedPlayList() {
        if (System.currentTimeMillis() - invitePlayListLastUpdateTime >= 1000 * 60) {
            sendRequest(IMPPackets.MusicSyncType.PLAYLIST_INVITES, mc.player.getGameProfile().getId());
            invitePlayListLastUpdateTime = System.currentTimeMillis();
        }
        return invitePlayList;
    }

    public void reset() {
        myPlayListLastUpdateTime = 0;
        canJoinPlayListLastUpdateTime = 0;
        invitePlayListLastUpdateTime = 0;
        musicUpdateTimes.clear();
        musicTotals.clear();
        musicLoading.clear();
        musicPages.clear();

        myPlayList = null;
        canJoinPlayList = null;
        invitePlayList = null;
        musics.clear();
    }

    private void sendRequest(IMPPackets.MusicSyncType type, UUID uuid) {
        sendRequest(type, uuid, 0);
    }

    private void sendRequest(IMPPackets.MusicSyncType type, UUID uuid, int offset) {
        if (uuid == null) return;
        NetworkManager.sendToServer(IMPPackets.MUSIC_SYNC_CTS, new IMPPackets.MusicSyncRequestMessage(type, uuid, offset).toRFBB());
    }

    public static record PlayListInfo(int playerCount, int playListCount, int musicCount) {
    }
}
