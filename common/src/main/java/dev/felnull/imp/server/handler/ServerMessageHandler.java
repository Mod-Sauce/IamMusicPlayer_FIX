package dev.felnull.imp.server.handler;

import dev.architectury.networking.NetworkManager;
import dev.architectury.utils.GameInstance;
import dev.felnull.imp.blockentity.MusicManagerBlockEntity;
import dev.felnull.imp.item.BoomboxItem;
import dev.felnull.imp.music.resource.AuthorityInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicPlayList;
import dev.felnull.imp.music.resource.MusicSource;
import dev.felnull.imp.networking.IMPPackets;
import dev.felnull.imp.server.music.MusicManager;
import dev.felnull.imp.server.music.ringer.MusicRingManager;
import dev.felnull.imp.server.webdav.ServerWebDAVProxyManager;
import dev.felnull.imp.webdav.WebDAVSourceUtil;
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
                MusicManager.getInstance().addMultipleMusic(packetContext.getPlayer().getServer(), message.playlist, message.musics, player);
        });
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
                var source = message.source;
                if (WebDAVSourceUtil.isWebDAV(source) && !WebDAVSourceUtil.isOwnedIdentifier(source.getIdentifier())) {
                    source = new MusicSource(source.getLoaderType(), WebDAVSourceUtil.encodeOwnedPath(packetContext.getPlayer().getUUID(), source.getIdentifier()), source.getDuration());
                }
                var m = new Music(UUID.randomUUID(), message.name, message.author, source, message.image, packetContext.getPlayer().getGameProfile().getId(), System.currentTimeMillis());
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

    public static void onMusicPlayListAddMessage(IMPPackets.MusicPlayListMessage message, NetworkManager.PacketContext packetContext) {
        packetContext.queue(() -> {
            var player = (ServerPlayer) packetContext.getPlayer();
            if (message.blockEntityExistence.check(player.level())) {
                Map<UUID, AuthorityInfo.AuthorityType> authTypes = new HashMap<>();
                message.invitePlayers.forEach(n -> authTypes.put(n, AuthorityInfo.AuthorityType.INVITATION));
                var auth = new AuthorityInfo(message.publiced, packetContext.getPlayer().getGameProfile().getId(), packetContext.getPlayer().getGameProfile().getName(), authTypes, message.initMember ? AuthorityInfo.AuthorityType.MEMBER : AuthorityInfo.AuthorityType.READ_ONLY);
                var pl = new MusicPlayList(UUID.randomUUID(), message.name, message.image, auth, new ArrayList<>(), System.currentTimeMillis());
                var mm = MusicManager.getInstance();
                mm.addPlayList(packetContext.getPlayer().getServer(), pl);
                mm.addPlayListToPlayer(packetContext.getPlayer().getServer(), pl.getUuid(), (ServerPlayer) packetContext.getPlayer());

                for (Music importMusic : message.importMusics) {
                    Music music = new Music(UUID.randomUUID(), importMusic.getName(), importMusic.getAuthor(), importMusic.getSource(), importMusic.getImage(), player.getGameProfile().getId(), System.currentTimeMillis());
                    mm.addMusicToPlayList(player, pl.getUuid(), music);
                }

                var be = (MusicManagerBlockEntity) ((ServerPlayer) packetContext.getPlayer()).level().getBlockEntity(message.blockEntityExistence.blockPos());
                if (be != null)
                    be.setSelectedPlayList((ServerPlayer) packetContext.getPlayer(), pl.getUuid());
            }
        });
    }

    public static void onWebDAVProfileSync(IMPPackets.WebDAVProfileSyncMessage message, NetworkManager.PacketContext packetContext) {
        packetContext.queue(() -> ServerWebDAVProxyManager.updateProfile((ServerPlayer) packetContext.getPlayer(), message.baseUrl, message.rootPath, message.username, message.password));
    }

    public static void onWebDAVProxyRequest(IMPPackets.WebDAVProxyRequestMessage message, NetworkManager.PacketContext packetContext) {
        ServerPlayer player = (ServerPlayer) packetContext.getPlayer();
        java.util.concurrent.CompletableFuture.runAsync(() -> ServerWebDAVProxyManager.streamToClient(player, message.sessionId));
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
                case MUSIC_BY_PLAYLIST -> {
                    var mpl = mm.getSaveData(packetContext.getPlayer().getServer()).getPlayLists().get(message.syncId);
                    if (mpl != null && mpl.getAuthority().getAuthorityType(pl.getGameProfile().getId()).isMoreReadOnly()) {
                        List<Music> musics = new ArrayList<>();
                        mpl.getMusicList().forEach(n -> {
                            var music = mm.getSaveData(packetContext.getPlayer().getServer()).getMusics().get(n);
                            if (music != null)
                                musics.add(music);
                        });

                        sendMusicSyncData(pl, message.syncType, message.syncId, new ArrayList<>(), musics);
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

    private static void sendMusicSyncData(ServerPlayer player, IMPPackets.MusicSyncType syncType, UUID uuid, List<MusicPlayList> playLists, List<Music> musics) {
        NetworkManager.sendToPlayer(player, IMPPackets.MUSIC_SYNC_STC, new IMPPackets.MusicSyncResponseMessage(syncType, uuid, playLists, musics).toRFBB());
    }
}
