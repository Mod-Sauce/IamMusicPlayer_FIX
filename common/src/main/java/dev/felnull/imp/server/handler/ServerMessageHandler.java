package dev.felnull.imp.server.handler;

import dev.architectury.networking.NetworkManager;
import dev.architectury.utils.GameInstance;
import dev.felnull.imp.blockentity.MusicManagerBlockEntity;
import dev.felnull.imp.item.BoomboxItem;
import dev.felnull.imp.music.resource.AuthorityInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicPlayList;
import dev.felnull.imp.networking.IMPPackets;
import dev.felnull.imp.server.music.MusicManager;
import dev.felnull.imp.server.music.ringer.MusicRingManager;
import dev.felnull.imp.util.IMPNbtUtil;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class ServerMessageHandler {
    public static void onHandLidCycleMessage(IMPPackets.LidCycleMessage message, NetworkManager.PacketContext packetContext) {
        packetContext.queue(() -> {
            var item = message.itemLocation.getItem(packetContext.getPlayer());
            if (item.getItem() instanceof BoomboxItem) {
                var id = BoomboxItem.getRingerUUID(item);
                if (id == null || !id.equals(message.boomboxId)) return;
                var data = BoomboxItem.getData(item, packetContext.getPlayer().level().registryAccess());
                data.cycleLidOpen(packetContext.getPlayer().level());
                BoomboxItem.setData(item, data);
            }
        });
    }

    public static void onMusicPlayListChangeAuthority(IMPPackets.MusicPlayListChangeAuthorityMessage message, NetworkManager.PacketContext packetContext) {
        packetContext.queue(() -> {
            ServerPlayer player = (ServerPlayer) packetContext.getPlayer();
            if (message.blockEntityExistence.check(player.level()))
                MusicManager.getInstance().changeAuthority(packetContext.getPlayer().getServer(), message.playlist, message.player, message.authorityType, player);
        });
    }

    public static void onMultipleMusicAdd(IMPPackets.MultipleMusicAddMessage message, NetworkManager.PacketContext packetContext) {
        packetContext.queue(() -> {
            ServerPlayer player = (ServerPlayer) packetContext.getPlayer();
            if (message.blockEntityExistence.check(player.level()))
                MusicManager.getInstance().addMultipleMusic(packetContext.getPlayer().getServer(), message.playlist,
                        message.musics, message.afterMusics, player);
        });
    }

    /**
     * The client's learned part list for one B站 video; nothing is trusted beyond shape — an unusable video number or fewer than two entries is dropped.
     */
    public static void onBiliBiliPartsMessage(IMPPackets.BiliBiliPartsMessage message, NetworkManager.PacketContext packetContext) {
        packetContext.queue(() -> {
            var names = message.names;
            var durations = message.durations;
            var list = new java.util.ArrayList<dev.felnull.imp.server.music.BiliBiliPartCache.Part>(names.size());
            for (int i = 0; i < names.size(); i++)
                list.add(new dev.felnull.imp.server.music.BiliBiliPartCache.Part(names.get(i),
                        i < durations.size() && durations.get(i) != null ? durations.get(i) : 0L));
            var changed = dev.felnull.imp.server.music.BiliBiliPartCache.getInstance().remember(message.bvid, list);
            // The first part's length **is** the length of the playlist entry (which was imported with the length of
            // all parts together) — correcting it is what makes the boombox's position and its "track is over"
            // decision agree with the audio again. See MusicManager#fixBiliBiliDuration.
            //
            // Only a changed list can change anything: a client is free to re-send the same report, and the
            // correction walks every playlist on the server (MusicManager#fixBiliBiliDuration), so a repeated report
            // must not cost a walk. MusicManager additionally guards the same video against being corrected twice in
            // a row within a short window.
            if (changed && !list.isEmpty())
                MusicManager.getInstance().fixBiliBiliDuration(packetContext.getPlayer().getServer(),
                        dev.felnull.imp.util.BiliBiliIds.stripPage(message.bvid), list.get(0).durationMs());
        });
    }

    /**
     * 客户端报的赞助/广告片段：服务端把音响位置**推到片段之后**（时间轴按内容走，曲尾不留静音）；空表 = 忘掉这首；除形状外什么都不信（起止成对、时长为正）。
     */
    public static void onBiliBiliSponsorMessage(IMPPackets.BiliBiliSponsorMessage message, NetworkManager.PacketContext packetContext) {
        packetContext.queue(() -> {
            var starts = message.starts;
            var ends = message.ends;
            int n = Math.min(starts.size(), ends.size());
            var list = new java.util.ArrayList<long[]>(n);
            for (int i = 0; i < n; i++) {
                var start = starts.get(i);
                var end = ends.get(i);
                if (start == null || end == null || end <= start)
                    continue;
                list.add(new long[]{Math.max(0L, start), end});
            }
            dev.felnull.imp.server.music.BiliBiliSponsorCache.getInstance().remember(message.identifier, list);
        });
    }

    /** 客户端报上来的 B站 投稿时间（服务端自己查不到）：只用于音响「投稿日期」那一档的排序。 */
    public static void onBiliBiliPubdatesMessage(IMPPackets.BiliBiliPubdatesMessage message, NetworkManager.PacketContext packetContext) {
        packetContext.queue(() -> {
            var cache = dev.felnull.imp.server.music.BiliBiliPubdateCache.getInstance();
            int n = Math.min(message.bvids.size(), message.pubdates.size());
            for (int i = 0; i < n; i++) {
                var ms = message.pubdates.get(i);
                cache.remember(message.bvids.get(i), ms == null ? 0L : ms);
            }
        });
    }

    /**
     * Part jump: only the part number is trusted — the target is resolved from the player, and {@code BoomboxData#jumpBiliBiliPart} re-checks power, remote mode, authority and the range.
     */
    public static void onBoomboxPartJumpMessage(IMPPackets.BoomboxPartJumpMessage message, NetworkManager.PacketContext packetContext) {
        packetContext.queue(() -> {
            ServerPlayer player = (ServerPlayer) packetContext.getPlayer();
            if (message.page < 1)
                return;
            var item = findPlayingHandheldBoombox(player);
            if (item.isEmpty())
                return;
            // The item form of the boombox is rebuilt from NBT on every read and writes itself back through
            // `update()` (see MusicInfoOverlay for the client side of the same arrangement).
            BoomboxItem.getData(item, player.level().registryAccess()).jumpBiliBiliPart(player, message.page);
        });
    }

    /** The first **powered** handheld boombox in this player's inventory, then the off hand; {@code EMPTY} when there is none. */
    private static net.minecraft.world.item.ItemStack findPlayingHandheldBoombox(ServerPlayer player) {
        var boombox = dev.felnull.imp.block.IMPBlocks.BOOMBOX.get().asItem();
        for (var stack : player.getInventory().items) {
            if (stack.is(boombox) && BoomboxItem.isPowered(stack))
                return stack;
        }
        var off = player.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND);
        if (off.is(boombox) && BoomboxItem.isPowered(off))
            return off;
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    public static void onMusicOrPlayListDeleteMessage(IMPPackets.MusicOrPlayListDeleteMessage message, NetworkManager.PacketContext packetContext) {
        packetContext.queue(() -> {
            ServerPlayer player = (ServerPlayer) packetContext.getPlayer();
            if (message.blockEntityExistence.check(player.level())) {
                var mm = MusicManager.getInstance();
                if (message.music) {
                    mm.deleteMusic(packetContext.getPlayer().getServer(), message.playListID, message.musicID, player);
                } else {
                    mm.deletePlayList(packetContext.getPlayer().getServer(), message.playListID, player);
                }
            }
        });
    }

    /**
     * Batch deletion: the same authority check per entry as {@code deleteMusic} (unpermitted entries are skipped); only really deleted songs are recorded, from the server's copy.
     */
    public static void onMusicDeleteBatchMessage(IMPPackets.MusicDeleteBatchMessage message, NetworkManager.PacketContext packetContext) {
        packetContext.queue(() -> {
            ServerPlayer player = (ServerPlayer) packetContext.getPlayer();
            if (message.blockEntityExistence.check(player.level())) {
                var mm = MusicManager.getInstance();
                var server = packetContext.getPlayer().getServer();
                // Copy before iterating: the deletion mutates the playlist's musicList, so using the original collection risks a concurrent modification.
                var deleted = mm.deleteMusics(server, message.playListID, new java.util.ArrayList<>(message.musicIDs), player);
                mm.rememberDeleted(player.getGameProfile().getId(), message.playListID, deleted);
            }
        });
    }

    /**
     * Undo batch deletion from the copy {@link MusicManager#rememberDeleted} recorded; the packet's objects are a uuid list only (a modified client could otherwise overwrite a song), and the entry is consumed on use.
     */
    public static void onMusicDeleteBatchUndoMessage(IMPPackets.MusicDeleteUndoMessage message, NetworkManager.PacketContext packetContext) {
        packetContext.queue(() -> {
            ServerPlayer player = (ServerPlayer) packetContext.getPlayer();
            if (!message.blockEntityExistence.check(player.level()))
                return;
            var mm = MusicManager.getInstance();
            var server = packetContext.getPlayer().getServer();
            var pl = mm.getSaveData(server).getPlayLists().get(message.playListID);
            if (pl == null)
                return;
            var pid = player.getGameProfile().getId();
            // Authority only: the restored songs come from the server table, so no owner self-declaration is involved.
            if (!pl.getAuthority().getAuthorityType(pid).canAddMusic())
                return;
            var musics = new ArrayList<Music>();
            for (Music m : new java.util.ArrayList<>(message.musics)) {
                if (m != null && m.getUuid() != null) musics.add(m);
            }
            // One setDirty() for the whole batch, see MusicManager#restoreDeleted.
            mm.restoreDeleted(server, pid, message.playListID, musics);
        });
    }

    public static void onMusicUpdateResultMessage(IMPPackets.MusicRingUpdateResultMessage message, NetworkManager.PacketContext packetContext) {
        packetContext.queue(() -> MusicRingManager.getInstance().onUpdate((ServerPlayer) packetContext.getPlayer(), message.uuid(), message.waitId(), message.ringResponseStateType()));
    }

    public static void onMusicReadyResultMessage(IMPPackets.MusicRingReadyResultMessage message, NetworkManager.PacketContext packetContext) {
        packetContext.queue(() -> MusicRingManager.getInstance().addReadyPlayer((ServerPlayer) packetContext.getPlayer(), message.uuid, message.waitID, message.result, message.retry, message.elapsed));
    }

    public static void onMusicAddMessage(IMPPackets.MusicMessage message, NetworkManager.PacketContext packetContext) {
        packetContext.queue(() -> {
            var mm = MusicManager.getInstance();
            var pl = mm.getSaveData(packetContext.getPlayer().getServer()).getPlayLists().get(message.playlist);
            if (pl != null && pl.getAuthority().getAuthorityType(packetContext.getPlayer().getGameProfile().getId()).canAddMusic()) {
                var m = new Music(UUID.randomUUID(), message.name, message.author, message.source, message.image, packetContext.getPlayer().getGameProfile().getId(), System.currentTimeMillis());
                mm.addMusicToPlayList((ServerPlayer) packetContext.getPlayer(), pl.getUuid(), m);
            }
        });
    }

    public static void onMusicEditMessage(IMPPackets.MusicMessage message, NetworkManager.PacketContext packetContext) {
        packetContext.queue(() -> {
            ServerPlayer player = (ServerPlayer) packetContext.getPlayer();
            if (message.blockEntityExistence.check(player.level()))
                MusicManager.getInstance().editMusic(packetContext.getPlayer().getServer(), message.uuid, message.playlist, message.name, message.image, player);
        });
    }

    public static void onMusicPlayListEditMessage(IMPPackets.MusicPlayListMessage message, NetworkManager.PacketContext packetContext) {
        packetContext.queue(() -> {
            ServerPlayer player = (ServerPlayer) packetContext.getPlayer();
            if (message.blockEntityExistence.check(player.level()))
                MusicManager.getInstance().editPlayList(packetContext.getPlayer().getServer(), message.uuid, message.name, message.image, message.invitePlayers, message.publiced, message.initMember, player);
        });
    }

    /**
     * Refresh: remembers (or corrects) the import source and puts a new cover on it; **not** {@code editPlayList}, which would reset invites/visibility for a bookkeeping write.
     */
    public static void onMusicPlayListSourceMessage(IMPPackets.PlayListSourceMessage message, NetworkManager.PacketContext packetContext) {
        packetContext.queue(() -> {
            var player = (ServerPlayer) packetContext.getPlayer();
            if (message.blockEntityExistence.check(player.level()))
                MusicManager.getInstance().setPlayListSource(player.getServer(), message.uuid, message.source, message.image, message.name, player);
        });
    }

    public static void onMusicPlayListAddMessage(IMPPackets.MusicPlayListMessage message, NetworkManager.PacketContext packetContext) {
        packetContext.queue(() -> {
            var player = (ServerPlayer) packetContext.getPlayer();
            if (!message.blockEntityExistence.check(player.level())) return;
            var mm = MusicManager.getInstance();
            var save = mm.getSaveData(player.getServer());
            // The id comes from the client: an import bigger than one packet creates the playlist with the first batch
            // and appends the rest to this very id (see ImportPlayListBaseMMMonitor). A uuid that is already taken is
            // rejected — a client must not be able to write into someone else's playlist by guessing its id.
            if (save.getPlayLists().containsKey(message.uuid)) {
                dev.felnull.imp.client.music.link.LinkDebug.log("playlist add refused", "why", "uuid already taken",
                        "uuid", message.uuid, "player", player.getGameProfile().getName());
                return;
            }
            Map<UUID, AuthorityInfo.AuthorityType> authTypes = new HashMap<>();
            message.invitePlayers.forEach(n -> authTypes.put(n, AuthorityInfo.AuthorityType.INVITATION));
            var auth = new AuthorityInfo(message.publiced, packetContext.getPlayer().getGameProfile().getId(), packetContext.getPlayer().getGameProfile().getName(), authTypes, message.initMember ? AuthorityInfo.AuthorityType.MEMBER : AuthorityInfo.AuthorityType.READ_ONLY);
            var pl = new MusicPlayList(message.uuid, message.name, message.image, auth, new ArrayList<>(), System.currentTimeMillis(), message.source);
            mm.addPlayList(packetContext.getPlayer().getServer(), pl);
            mm.addPlayListToPlayer(packetContext.getPlayer().getServer(), pl.getUuid(), (ServerPlayer) packetContext.getPlayer());
            // One batch, one dirty (the per-song path broadcasts a full update after every single song).
            mm.addMusicsToPlayList(packetContext.getPlayer().getServer(), pl.getUuid(), message.importMusics, player);

            var be = (MusicManagerBlockEntity) ((ServerPlayer) packetContext.getPlayer()).level().getBlockEntity(message.blockEntityExistence.blockPos());
            if (be != null)
                be.setSelectedPlayList((ServerPlayer) packetContext.getPlayer(), pl.getUuid());
            dev.felnull.imp.client.music.link.LinkDebug.log("playlist created", "uuid", pl.getUuid(),
                    "songs", message.importMusics.size(), "source", message.source.isBlank() ? "(none)" : message.source);
        });
    }

    public static void onMusicSyncRequestMessage(IMPPackets.MusicSyncRequestMessage message, NetworkManager.PacketContext packetContext) {
        packetContext.queue(() -> {
            var mm = MusicManager.getInstance();
            var pl = (ServerPlayer) packetContext.getPlayer();
            switch (message.syncType) {
                case PLAYLIST_MY_LIST ->
                        sendMusicSyncData(pl, message.syncType, message.syncId, mm.getPlayerPlayLists(pl, MusicManager.PlayListGetType.JOIN), new ArrayList<>());
                case PLAYLIST_CAN_JOIN ->
                        sendMusicSyncData(pl, message.syncType, message.syncId, mm.getPlayerPlayLists(pl, MusicManager.PlayListGetType.NO_JOIN), new ArrayList<>());
                // Playlists I was invited to (not accepted yet): a private playlist never enters the "joinable" list, invitees get it through this one,
                // and the client uses it to mark "invite" + raise the HUD notice (see PlayListInviteOverlay).
                case PLAYLIST_INVITES ->
                        sendMusicSyncData(pl, message.syncType, message.syncId, mm.getPlayerPlayLists(pl, MusicManager.PlayListGetType.INVITES), new ArrayList<>());
                case MUSIC_BY_PLAYLIST -> {
                    var mm2 = mm.getSaveData(packetContext.getPlayer().getServer());
                    var mpl = mm2.getPlayLists().get(message.syncId);
                    if (mpl != null && mpl.getAuthority().getAuthorityType(pl.getGameProfile().getId()).isMoreReadOnly()) {
                        List<Music> all = new ArrayList<>();
                        mpl.getMusicList().forEach(n -> {
                            var music = mm2.getMusics().get(n);
                            if (music != null)
                                all.add(music);
                        });
                        // One packet cannot carry an unbounded song list (2 MiB accounted NBT per tag ⇒ ~1100 songs):
                        // send the largest page that fits and let the client ask for the next one (offset/total).
                        int offset = Math.max(0, Math.min(message.offset, all.size()));
                        int count = IMPNbtUtil.fittingMusicCount("Musics", all, offset);
                        int end = Math.min(offset + count, all.size());
                        sendMusicSyncData(pl, message.syncType, message.syncId, new ArrayList<>(),
                                new ArrayList<>(all.subList(offset, end)), offset, all.size());
                    }
                }
            }
        });
    }

    public static void onMusicDataUpdate() {
        if (GameInstance.getServer() != null) {
            GameInstance.getServer().getPlayerList().getPlayers().forEach(n -> sendMusicSyncData(n, IMPPackets.MusicSyncType.UPDATE, n.getGameProfile().getId(), new ArrayList<>(), new ArrayList<>()));
        }
    }

    /**
     * Resolves every UUID of this packet into one flat name map; names are derived data, attached but never stored — they ride along because the packet is serialized with {@code createSavedTag()}.
     */
    private static Map<UUID, String> collectPlayerNames(ServerPlayer player, List<MusicPlayList> playLists, List<Music> musics) {
        var server = player.getServer();
        if (server == null) return Map.of();
        Set<UUID> ids = new LinkedHashSet<>();
        if (playLists != null) {
            for (var playList : playLists) {
                if (playList == null || playList.getAuthority() == null) continue;
                var auth = playList.getAuthority();
                if (auth.getOwner() != null) ids.add(auth.getOwner());
                for (var member : auth.getRawAuthority().keySet()) {
                    if (member != null) ids.add(member);
                }
            }
        }
        if (musics != null) {
            for (var music : musics) {
                if (music != null && music.getOwner() != null) ids.add(music.getOwner());
            }
        }
        if (ids.isEmpty()) return Map.of();
        Map<UUID, String> names = new LinkedHashMap<>();
        for (var id : ids) {
            var online = server.getPlayerList().getPlayer(id);
            if (online != null) {
                names.put(id, online.getGameProfile().getName());
                continue;
            }
            server.getProfileCache().get(id).ifPresent(profile -> names.put(id, profile.getName()));
        }
        return names;
    }

    private static void sendMusicSyncData(ServerPlayer player, IMPPackets.MusicSyncType syncType, UUID uuid, List<MusicPlayList> playLists, List<Music> musics) {
        sendMusicSyncData(player, syncType, uuid, playLists, musics, 0, 0);
    }

    private static void sendMusicSyncData(ServerPlayer player, IMPPackets.MusicSyncType syncType, UUID uuid, List<MusicPlayList> playLists, List<Music> musics, int musicOffset, int musicTotal) {
        NetworkManager.sendToPlayer(player, IMPPackets.MUSIC_SYNC_STC, new IMPPackets.MusicSyncResponseMessage(syncType, uuid, playLists, musics, collectPlayerNames(player, playLists, musics), musicOffset, musicTotal).toRFBB());
    }
}
