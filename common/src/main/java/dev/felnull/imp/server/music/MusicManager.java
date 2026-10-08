package dev.felnull.imp.server.music;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.advancements.IMPCriteriaTriggers;
import dev.felnull.imp.music.resource.AuthorityInfo;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicPlayList;
import dev.felnull.imp.server.saveddata.MusicSaveData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.function.BiPredicate;

public class MusicManager {
    private static final MusicManager INSTANCE = new MusicManager();

    /**
     * How long a batch deletion stays undoable; the client's undo window is the same length ({@code MusicManageMMMonitor.UNDO_WINDOW_MS}).
     */
    public static final long UNDO_WINDOW_MS = 6000L;

    /**
     * Undo table for batch deletion: the **server-side copies** per (player, playlist) — the packet contributes only uuids. In-memory, server thread only, entries expire after {@link #UNDO_WINDOW_MS}.
     */
    private final Map<DeletedPlayListKey, DeletedMusics> deletedUndoCache = new HashMap<>();

    private record DeletedPlayListKey(UUID playerId, UUID playlistId) {
    }

    private record DeletedMusics(List<Music> musics, long time) {
    }

    public static MusicManager getInstance() {
        return INSTANCE;
    }

    public MusicSaveData getSaveData(MinecraftServer server) {
        return MusicSaveData.get(server);
    }

    public void addMusic(MinecraftServer server, Music music) {
        getSaveData(server).getMusics().put(music.getUuid(), music);
        getSaveData(server).setDirty();
    }

    public MusicPlayList getPlaylistByMusic(MinecraftServer server, UUID musicId) {
        for (MusicPlayList value : getSaveData(server).getPlayLists().values()) {
            if (value.getMusicList().contains(musicId))
                return value;
        }
        return null;
    }

    public void addPlayList(MinecraftServer server, MusicPlayList playList) {
        getSaveData(server).getPlayLists().put(playList.getUuid(), playList);
        getSaveData(server).setDirty();
    }

    public void removeMusic(MinecraftServer server, UUID musicID) {
        getSaveData(server).getMusics().remove(musicID);
        getSaveData(server).getPlayLists().values().stream().map(MusicPlayList::getMusicList).forEach(n -> n.remove(musicID));
        getSaveData(server).setDirty();
    }

    public void removePlayList(MinecraftServer server, UUID playlistID) {
        getSaveData(server).getPlayLists().remove(playlistID);
        getSaveData(server).setDirty();
    }

    public void addMusicToPlayList(ServerPlayer player, UUID playlistId, Music music) {
        addMusicToPlayList(player.server, playlistId, music);
        IMPCriteriaTriggers.ADD_MUSIC.trigger(player);
    }

    public void addMusicToPlayList(MinecraftServer server, UUID playlistId, Music music) {
        if (getSaveData(server).getPlayLists().containsKey(playlistId)) {
            addMusic(server, music);
            getSaveData(server).getPlayLists().get(playlistId).getMusicList().add(music.getUuid());
            getSaveData(server).setDirty();
        }
    }

    /**
     * Appends a whole batch with **one** {@code setDirty()} (that call broadcasts a full update, so per-song would send ~1000 broadcasts); the songs are re-owned by {@code player}.
     */
    public void addMusicsToPlayList(MinecraftServer server, UUID playListId, List<Music> musics, ServerPlayer player) {
        if (musics == null || musics.isEmpty()) return;
        var save = getSaveData(server);
        var pl = save.getPlayLists().get(playListId);
        if (pl == null) {
            dev.felnull.imp.client.music.link.LinkDebug.log("music add skipped", "why", "no such playlist",
                    "playlist", playListId, "songs", musics.size());
            return;
        }
        if (!pl.getAuthority().getAuthorityType(player.getGameProfile().getId()).canAddMusic()) return;
        var owner = player.getGameProfile().getId();
        var now = System.currentTimeMillis();
        var list = pl.getMusicList();
        for (var music : musics) {
            var added = new Music(UUID.randomUUID(), music.getName(), music.getAuthor(), music.getSource(),
                    music.getImage(), owner, now);
            save.getMusics().put(added.getUuid(), added);
            list.add(added.getUuid());
        }
        save.setDirty();
        IMPCriteriaTriggers.ADD_MUSIC.trigger(player);
    }

    public List<MusicPlayList> getPlayerPlayLists(ServerPlayer player, PlayListGetType getType) {
        var uuid = player.getGameProfile().getId();
        List<MusicPlayList> playLists = new ArrayList<>();
        getSaveData(player.server).getPlayLists().forEach((n, m) -> {
            if (getType.check(m, m.getAuthority().getAuthorityType(uuid)))
                playLists.add(m);
        });
        return Collections.unmodifiableList(playLists);
    }

    public void addPlayListToPlayer(MinecraftServer server, UUID playListId, ServerPlayer player) {
        var uuid = player.getGameProfile().getId();
        var pl = getSaveData(server).getPlayLists().get(playListId);
        if (pl != null && pl.getAuthority().canJoin(uuid)) {
            pl.getAuthority().getRawAuthority().put(uuid, pl.getAuthority().getInitialAuthority());
            getSaveData(server).setDirty();
        }
    }

    public void deletePlayList(MinecraftServer server, UUID playListId, ServerPlayer player) {
        var pl = getSaveData(server).getPlayLists().get(playListId);
        if (pl == null) return;
        if (!pl.getAuthority().getAuthorityType(player.getGameProfile().getId()).canDelete()) {
            exitPlayList(server, playListId, player);
            return;
        }
        // Default semantics (deleteMusicPlayListOnly=true): drop only this playlist's references and collect the musics nobody references any more.
        // The playlist must leave the map first — otherwise it still counts as a reference itself and the orphans can never be collected.
        var musicIds = new ArrayList<>(pl.getMusicList());
        boolean global = !IamMusicPlayer.getConfig().deleteMusicPlayListOnly;
        getSaveData(server).getPlayLists().remove(playListId);
        for (UUID uuid : musicIds) {
            if (global)
                getSaveData(server).getMusics().remove(uuid);
            else
                collectOrphanMusic(server, uuid);
        }
        getSaveData(server).setDirty();
    }

    /**
     * Removes the music from the **current playlist** only (the default semantics); only turning off {@code deleteMusicPlayListOnly} returns to "delete globally".
     */
    private void collectOrphanMusic(MinecraftServer server, UUID musicId) {
        for (MusicPlayList pl : getSaveData(server).getPlayLists().values()) {
            if (pl.getMusicList().contains(musicId))
                return;
        }
        getSaveData(server).getMusics().remove(musicId);
    }

    public void exitPlayList(MinecraftServer server, UUID playListId, ServerPlayer player) {
        var pl = getSaveData(server).getPlayLists().get(playListId);
        if (pl == null) return;
        getSaveData(server).getPlayLists().get(playListId).getAuthority().getRawAuthority().remove(player.getGameProfile().getId());
        getSaveData(server).setDirty();
    }

    public boolean deleteMusic(MinecraftServer server, UUID playListId, UUID musicId, ServerPlayer player) {
        var pl = getSaveData(server).getPlayLists().get(playListId);
        if (pl == null || !pl.getMusicList().contains(musicId)) return false;
        var pid = player.getGameProfile().getId();
        var m = getSaveData(server).getMusics().get(musicId);
        if (m == null) return false;
        boolean flg1 = pl.getAuthority().getAuthorityType(pid).canMusicDelete();
        boolean flg2 = m.getOwner().equals(pid);
        if (!flg1 && !flg2) return false;
        pl.getMusicList().remove(musicId);
        // [default: remove from this playlist only] see IMPConfig#deleteMusicPlayListOnly — design requirement:
        // "also deleting from the global library" would make the same music disappear from other playlists too ("too broad").
        // Turning the switch off = back to global deletion (that also applies to single and batch deletion; both paths share this method).
        if (IamMusicPlayer.getConfig().deleteMusicPlayListOnly)
            collectOrphanMusic(server, musicId);
        else
            getSaveData(server).getMusics().remove(musicId);
        // One setDirty() per call is fine for a single deletion; batch deletion must not call this in a loop (it pushes a full playlist sync to every player).
        getSaveData(server).setDirty();
        return true;
    }

    /**
     * Batch deletion: the same per-entry authority check as {@link #deleteMusic}, but **one** {@code setDirty()} — per-entry calls would cause N full playlist syncs.
     *
     * @return the server copies of the songs that were really deleted, for the undo table
     */
    public List<Music> deleteMusics(MinecraftServer server, UUID playListId, List<UUID> musicIds, ServerPlayer player) {
        var pl = getSaveData(server).getPlayLists().get(playListId);
        if (pl == null) return List.of();
        var pid = player.getGameProfile().getId();
        boolean canDelete = pl.getAuthority().getAuthorityType(pid).canMusicDelete();
        boolean playlistOnly = IamMusicPlayer.getConfig().deleteMusicPlayListOnly;
        var deleted = new ArrayList<Music>();
        for (UUID musicId : musicIds) {
            if (musicId == null) continue;
            var m = getSaveData(server).getMusics().get(musicId);
            if (m == null) continue;
            if (!canDelete && !m.getOwner().equals(pid)) continue;
            pl.getMusicList().remove(musicId);
            if (!playlistOnly)
                getSaveData(server).getMusics().remove(musicId);
            deleted.add(m);
        }
        if (playlistOnly)
            deleted.forEach(n -> collectOrphanMusic(server, n.getUuid()));
        if (!deleted.isEmpty())
            getSaveData(server).setDirty();
        return deleted;
    }

    /**
     * Records the songs of one batch deletion so the player can undo it for {@link #UNDO_WINDOW_MS}; the list holds the **server-side copies**.
     */
    public void rememberDeleted(UUID playerId, UUID playlistId, List<Music> musics) {
        dropExpiredUndo();
        if (playerId == null || playlistId == null || musics == null || musics.isEmpty()) return;
        deletedUndoCache.put(new DeletedPlayListKey(playerId, playlistId),
                new DeletedMusics(List.copyOf(musics), System.currentTimeMillis()));
    }

    /**
     * Undo a batch deletion: restores the songs recorded for this (player, playlist) whose uuid is in {@code requests}; **one** {@code setDirty()}, and the entry is consumed once anything was restored (no replay).
     *
     * @return how many songs were restored
     */
    public int restoreDeleted(MinecraftServer server, UUID playerId, UUID playlistId, List<Music> requests) {
        if (playerId == null || playlistId == null || requests == null || requests.isEmpty()) return 0;
        var key = new DeletedPlayListKey(playerId, playlistId);
        var entry = deletedUndoCache.get(key);
        if (entry == null) return 0;
        if (System.currentTimeMillis() - entry.time() > UNDO_WINDOW_MS) {
            deletedUndoCache.remove(key, entry);
            return 0;
        }
        var pl = getSaveData(server).getPlayLists().get(playlistId);
        if (pl == null) return 0;
        int restored = 0;
        for (Music request : requests) {
            var copy = entry.musics().stream().filter(n -> n.getUuid().equals(request.getUuid())).findFirst().orElse(null);
            // Not in the server table ⇒ not deleted by this player (or already restored) ⇒ skip: never trust packet content.
            if (copy == null) continue;
            if (pl.getMusicList().contains(copy.getUuid())) continue;
            // putIfAbsent: an existing song with this uuid (someone else's) must never be overwritten.
            getSaveData(server).getMusics().putIfAbsent(copy.getUuid(), copy);
            pl.getMusicList().add(copy.getUuid());
            restored++;
        }
        // Consume only what was really restored: a request that matches nothing must not destroy a still-pending
        // undo (a forged uuid list would otherwise be enough to throw it away).
        if (restored > 0)
            deletedUndoCache.remove(key, entry);
        if (restored > 0)
            getSaveData(server).setDirty();
        return restored;
    }

    /** Drop undo entries past their window; called on access (server thread only), so the table cannot grow without bound. */
    private void dropExpiredUndo() {
        long now = System.currentTimeMillis();
        deletedUndoCache.values().removeIf(n -> now - n.time() > UNDO_WINDOW_MS);
    }

    /** Free the undo table when the server stops; the table holds a server instance's transient state only. */
    public void clearUndoCache() {
        deletedUndoCache.clear();
    }

    public void changeAuthority(MinecraftServer server, UUID playListId, UUID targetPlayerId, AuthorityInfo.AuthorityType authorityType, ServerPlayer player) {
        // Only these five levels are accepted; OWNER must not be grantable here (nor transferable) — `canDelete()` recognizes OWNER only,
        // so a second OWNER could delete the playlist and the creator could never demote him (canChangeAuth needs a strictly higher level).
        // INVITATION is not set from here either (that belongs to filling in the invite list while editing).
        if (authorityType != AuthorityInfo.AuthorityType.READ_ONLY
                && authorityType != AuthorityInfo.AuthorityType.MEMBER
                && authorityType != AuthorityInfo.AuthorityType.ADMIN
                && authorityType != AuthorityInfo.AuthorityType.BAN
                && authorityType != AuthorityInfo.AuthorityType.NONE)
            return;
        var pl = getSaveData(server).getPlayLists().get(playListId);
        if (pl == null) return;
        if (!pl.getAuthority().getPlayersAuthority().containsKey(targetPlayerId)) return;
        var ma = pl.getAuthority().getAuthorityType(player.getGameProfile().getId());
        var ta = pl.getAuthority().getAuthorityType(targetPlayerId);
        if (!ma.canChangeAuth(ta)) return;
        if (authorityType.isMoreAdmin() && !ma.isMoreOwner()) return;
        if (authorityType == AuthorityInfo.AuthorityType.NONE) {
            getSaveData(server).getPlayLists().get(playListId).getAuthority().getRawAuthority().remove(targetPlayerId);
        } else {
            getSaveData(server).getPlayLists().get(playListId).getAuthority().getRawAuthority().put(targetPlayerId, authorityType);
        }
        getSaveData(server).setDirty();
    }

    /**
     * Corrects a B站 entry's stored length: the submission list reports all parts together while the entry holds part 1, and that length drives the position and "track over".
     *
     * @return how many entries were corrected
     */
    public int fixBiliBiliDuration(MinecraftServer server, String identifier, long correctDurationMs) {
        if (server == null || identifier == null || correctDurationMs <= 0)
            return 0;
        var save = getSaveData(server);
        var musics = save.getMusics();
        int fixed = 0;
        for (var pl : save.getPlayLists().values()) {
            for (var musicId : pl.getMusicList()) {
                var m = musics.get(musicId);
                if (m == null || m.getSource() == null)
                    continue;
                if (!identifier.equals(m.getSource().getIdentifier()))
                    continue;   // only the entry of the video itself, never a part row
                if (m.getSource().getDuration() == correctDurationMs)
                    continue;
                // MusicSource has no setter: replace the music under the same id so every playlist keeps pointing at it
                musics.put(musicId, new Music(musicId, m.getName(), m.getAuthor(),
                        new dev.felnull.imp.music.resource.MusicSource(m.getSource().getLoaderType(), identifier,
                                correctDurationMs),
                        m.getImage(), m.getOwner(), m.getCreateDate()));
                fixed++;
            }
        }
        if (fixed > 0)
            save.setDirty();
        return fixed;
    }

    /**
     * @param afterMusics optional, one entry per song (or empty/null = append): the song each one is inserted **directly behind** — the 分P expansion needs its
     *                    parts right behind their video. Entries whose anchor is not in this playlist (or {@code null}) are appended as before.
     */
    public void addMultipleMusic(MinecraftServer server, UUID playListId, List<Music> musics, List<UUID> afterMusics,
                                 ServerPlayer player) {
        if (musics == null || musics.isEmpty()) return;
        var save = getSaveData(server);
        var pl = save.getPlayLists().get(playListId);
        if (pl == null) {
            dev.felnull.imp.client.music.link.LinkDebug.log("music add skipped", "why", "no such playlist",
                    "playlist", playListId, "songs", musics.size());
            return;
        }
        if (!pl.getAuthority().getAuthorityType(player.getGameProfile().getId()).canAddMusic()) return;
        // How many songs were already inserted behind each anchor in this batch: without it the second part would be
        // placed at the same index as the first and the two would come out in reverse order.
        var inserted = new java.util.HashMap<UUID, Integer>();
        var owner = player.getGameProfile().getId();
        var now = System.currentTimeMillis();
        var list = pl.getMusicList();
        for (int i = 0; i < musics.size(); i++) {
            Music music = musics.get(i);
            var am = new Music(UUID.randomUUID(), music.getName(), music.getAuthor(), music.getSource(), music.getImage(), owner, now);
            save.getMusics().put(am.getUuid(), am);
            UUID anchor = afterMusics != null && i < afterMusics.size() ? afterMusics.get(i) : null;
            int base = anchor == null ? -1 : list.indexOf(anchor);
            if (base < 0) {
                list.add(am.getUuid());
                continue;
            }
            int offset = inserted.merge(anchor, 1, Integer::sum);
            list.add(Math.min(base + offset, list.size()), am.getUuid());
        }
        // One setDirty() for the whole batch (it broadcasts a full update to every player, see deleteMusics).
        save.setDirty();
        IMPCriteriaTriggers.ADD_MUSIC.trigger(player);
    }


    public void editMusic(MinecraftServer server, UUID musicId, UUID playListId, String name, ImageInfo image, ServerPlayer player) {
        var pl = getSaveData(server).getPlayLists().get(playListId);
        if (pl == null || !pl.getMusicList().contains(musicId)) return;
        var pid = player.getGameProfile().getId();
        if (pl.getAuthority().getAuthorityType(pid).isBan()) return;
        var m = getSaveData(server).getMusics().get(musicId);
        if (m == null || !m.getOwner().equals(pid)) return;
        var nm = new Music(musicId, name, m.getAuthor(), m.getSource(), image, m.getOwner(), m.getCreateDate());
        getSaveData(server).getMusics().put(musicId, nm);
        getSaveData(server).setDirty();
    }

    /**
     * Writes only the import source (and the cover when sent); **not** {@link #editPlayList} — that one rebuilds the whole {@code AuthorityInfo} from the packet.
     */
    public void setPlayListSource(MinecraftServer server, UUID playListId, String source, ImageInfo image, ServerPlayer player) {
        setPlayListSource(server, playListId, source, image, "", player);
    }

    /**
     * Same as above plus an optional rename ({@code newName} empty = keep it); a legacy {@code "<UP 名> 的投稿"} name is stored data, so the generator cannot fix it.
     */
    public void setPlayListSource(MinecraftServer server, UUID playListId, String source, ImageInfo image, String newName, ServerPlayer player) {
        var pl = getSaveData(server).getPlayLists().get(playListId);
        if (pl == null) return;
        if (!pl.getAuthority().getAuthorityType(player.getGameProfile().getId()).canEdit()) return;
        var changed = false;
        if (source != null && !source.isBlank() && !source.equals(pl.getSource())) {
            pl.setSource(source);
            changed = true;
        }
        if (image != null && !image.isEmpty() && !image.equals(pl.getImage())) {
            pl.setImage(image);
            changed = true;
        }
        if (newName != null && !newName.isBlank() && !newName.equals(pl.getName())) {
            pl.setName(newName);
            changed = true;
        }
        if (changed)
            getSaveData(server).setDirty();
    }

    public void editPlayList(MinecraftServer server, UUID playListId, String name, ImageInfo image, List<UUID> invitePlayers, boolean publiced, boolean initMember, ServerPlayer player) {
        var pl = getSaveData(server).getPlayLists().get(playListId);
        if (pl == null) return;
        if (!pl.getAuthority().getAuthorityType(player.getGameProfile().getId()).canEdit()) return;
        var oldAuth = pl.getAuthority();
        Map<UUID, AuthorityInfo.AuthorityType> naus = new HashMap<>(oldAuth.getRawAuthority());
        List<UUID> rms = naus.entrySet().stream().filter(n -> n.getValue() == AuthorityInfo.AuthorityType.INVITATION).map(Map.Entry::getKey).toList();
        rms.forEach(naus::remove);
        invitePlayers.forEach(n -> {
            if (!naus.containsKey(n))
                naus.put(n, AuthorityInfo.AuthorityType.INVITATION);
        });
        var auth = new AuthorityInfo(publiced, oldAuth.getOwner(), oldAuth.getOwnerName(), naus, initMember ? AuthorityInfo.AuthorityType.MEMBER : AuthorityInfo.AuthorityType.READ_ONLY);
        getSaveData(server).getPlayLists().put(playListId, new MusicPlayList(playListId, name, image, auth, pl.getMusicList(), pl.getCreateDate()));
        getSaveData(server).setDirty();
    }

    /**
     * Visibility criteria for playlist lists (the filter gets the playlist and the querier's level); a private playlist never enters a discovery list, not even for an invitee.
     */
    public static enum PlayListGetType {
        /** Playlists I have already joined (≥ read-only) = "my playlists". */
        JOIN((pl, type) -> !type.isBan() && type.isMoreReadOnly()),
        /** The "joinable playlists" list: **public playlists only**, which I have not joined, am not banned from, and am not invited to (invites go through INVITES). */
        NO_JOIN((pl, type) -> pl.getAuthority().isPublic() && !type.isBan() && !type.isMoreReadOnly() && !type.isInvitation()),
        /** Playlists I was invited to (INVITATION) but have not accepted yet — both public and private; the client uses it to mark "invite" and raise the HUD notification. */
        INVITES((pl, type) -> type.isInvitation());

        private final BiPredicate<MusicPlayList, AuthorityInfo.AuthorityType> filter;

        private PlayListGetType(BiPredicate<MusicPlayList, AuthorityInfo.AuthorityType> filter) {
            this.filter = filter;
        }

        private boolean check(MusicPlayList playList, AuthorityInfo.AuthorityType type) {
            return filter.test(playList, type);
        }
    }
}
