package dev.felnull.imp.networking;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.handler.ClientMessageHandler;
import dev.felnull.imp.music.resource.*;
import dev.felnull.imp.server.handler.ServerMessageHandler;
import dev.felnull.imp.util.IMPNbtUtil;
import net.fabricmc.api.EnvType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.modsauce.otyacraftenginerenewed.item.location.PlayerItemLocation;
import org.modsauce.otyacraftenginerenewed.item.location.PlayerItemLocations;
import org.modsauce.otyacraftenginerenewed.networking.PacketMessage;
import org.modsauce.otyacraftenginerenewed.networking.existence.BlockEntityExistence;
import org.modsauce.otyacraftenginerenewed.server.level.TagSerializable;
import org.modsauce.otyacraftenginerenewed.util.OENbtUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class IMPPackets {
    public static final ResourceLocation MUSIC_SYNC_STC = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_sync_stc");
    public static final ResourceLocation MUSIC_SYNC_CTS = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_sync_cts");
    public static final ResourceLocation MUSIC_PLAYLIST_ADD = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_playlist_add");
    public static final ResourceLocation MUSIC_PLAYLIST_EDIT = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_playlist_edit");
    public static final ResourceLocation MUSIC_PLAYLIST_CHANGE_AUTHORITY = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_playlist_change_authority");
    public static final ResourceLocation MUSIC_ADD = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_add");
    public static final ResourceLocation MUSIC_EDIT = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_edit");
    public static final ResourceLocation MUSIC_OR_PLAYLIST_DELETE = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_or_playlist_delete");
    public static final ResourceLocation MUSIC_RING_READY = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_ring_ready");
    public static final ResourceLocation MUSIC_RING_READY_RESULT = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_ring_ready_result");
    public static final ResourceLocation MUSIC_RING_STATE = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_ring_state");
    public static final ResourceLocation MUSIC_RING_UPDATE_RESULT = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_ring_update_result");
    public static final ResourceLocation MULTIPLE_MUSIC_ADD = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "multiple_music_add");
    public static final ResourceLocation HAND_LID_CYCLE = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "hand_lid_cycle");
    public static final ResourceLocation WEBDAV_PROFILE_SYNC_CTS = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "webdav_profile_sync_cts");
    public static final ResourceLocation WEBDAV_PROXY_REQUEST_CTS = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "webdav_proxy_request_cts");
    public static final ResourceLocation WEBDAV_PROXY_START_STC = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "webdav_proxy_start_stc");
    public static final ResourceLocation WEBDAV_PROXY_CHUNK_STC = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "webdav_proxy_chunk_stc");
    public static final ResourceLocation WEBDAV_PROXY_END_STC = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "webdav_proxy_end_stc");
    public static final ResourceLocation WEBDAV_PROXY_ERROR_STC = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "webdav_proxy_error_stc");

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.c2s(), MUSIC_SYNC_CTS, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onMusicSyncRequestMessage(new MusicSyncRequestMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), MUSIC_PLAYLIST_ADD, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onMusicPlayListAddMessage(new MusicPlayListMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), MUSIC_PLAYLIST_EDIT, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onMusicPlayListEditMessage(new MusicPlayListMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), MUSIC_PLAYLIST_CHANGE_AUTHORITY, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onMusicPlayListChangeAuthority(new MusicPlayListChangeAuthorityMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), MUSIC_ADD, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onMusicAddMessage(new MusicMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), MUSIC_EDIT, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onMusicEditMessage(new MusicMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), MUSIC_RING_READY_RESULT, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onMusicReadyResultMessage(new MusicRingReadyResultMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), MUSIC_RING_UPDATE_RESULT, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onMusicUpdateResultMessage(new MusicRingUpdateResultMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), MUSIC_OR_PLAYLIST_DELETE, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onMusicOrPlayListDeleteMessage(new MusicOrPlayListDeleteMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), MULTIPLE_MUSIC_ADD, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onMultipleMusicAdd(new MultipleMusicAddMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), HAND_LID_CYCLE, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onHandLidCycleMessage(new LidCycleMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), WEBDAV_PROFILE_SYNC_CTS, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onWebDAVProfileSync(new WebDAVProfileSyncMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), WEBDAV_PROXY_REQUEST_CTS, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onWebDAVProxyRequest(new WebDAVProxyRequestMessage(friendlyByteBuf), packetContext));

        // These code snippets are useful as they prevent errors caused by the server missing STC packets.
        if(Platform.getEnv() == EnvType.SERVER) {
            NetworkManager.registerS2CPayloadType(MUSIC_SYNC_STC);
            NetworkManager.registerS2CPayloadType(MUSIC_RING_READY);
            NetworkManager.registerS2CPayloadType(MUSIC_RING_STATE);
            NetworkManager.registerS2CPayloadType(WEBDAV_PROXY_START_STC);
            NetworkManager.registerS2CPayloadType(WEBDAV_PROXY_CHUNK_STC);
            NetworkManager.registerS2CPayloadType(WEBDAV_PROXY_END_STC);
            NetworkManager.registerS2CPayloadType(WEBDAV_PROXY_ERROR_STC);
        }
    }

    public static void clientInit() {
        NetworkManager.registerReceiver(NetworkManager.s2c(), MUSIC_SYNC_STC, (friendlyByteBuf, packetContext) -> ClientMessageHandler.onMusicSyncResponseMessage(new MusicSyncResponseMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.s2c(), MUSIC_RING_READY, (friendlyByteBuf, packetContext) -> ClientMessageHandler.onMusicRingReadyResponseMessage(new MusicReadyMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.s2c(), MUSIC_RING_STATE, (friendlyByteBuf, packetContext) -> ClientMessageHandler.onMusicRingStateResponseMessage(new MusicRingStateMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.s2c(), WEBDAV_PROXY_START_STC, (friendlyByteBuf, packetContext) -> ClientMessageHandler.onWebDAVProxyStart(new WebDAVProxyStartMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.s2c(), WEBDAV_PROXY_CHUNK_STC, (friendlyByteBuf, packetContext) -> ClientMessageHandler.onWebDAVProxyChunk(new WebDAVProxyChunkMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.s2c(), WEBDAV_PROXY_END_STC, (friendlyByteBuf, packetContext) -> ClientMessageHandler.onWebDAVProxyEnd(new WebDAVProxyEndMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.s2c(), WEBDAV_PROXY_ERROR_STC, (friendlyByteBuf, packetContext) -> ClientMessageHandler.onWebDAVProxyError(new WebDAVProxyErrorMessage(friendlyByteBuf), packetContext));
    }

    public static class LidCycleMessage implements PacketMessage {
        public final UUID boomboxId;
        public final PlayerItemLocation itemLocation;

        public LidCycleMessage(FriendlyByteBuf buf) {
            this.boomboxId = buf.readUUID();
            this.itemLocation = PlayerItemLocations.loadFromTag(buf.readNbt());
        }

        public LidCycleMessage(UUID boomboxId, PlayerItemLocation itemLocation) {
            this.boomboxId = boomboxId;
            this.itemLocation = itemLocation;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(this.boomboxId);
            buf.writeNbt(PlayerItemLocations.saveToTag(this.itemLocation));
            return buf;
        }
    }

    public static class MusicPlayListChangeAuthorityMessage implements PacketMessage {
        public final UUID playlist;
        public final UUID player;
        public final AuthorityInfo.AuthorityType authorityType;
        public final BlockEntityExistence blockEntityExistence;

        public MusicPlayListChangeAuthorityMessage(FriendlyByteBuf bf) {
            this.playlist = bf.readUUID();
            this.player = bf.readUUID();
            this.authorityType = AuthorityInfo.AuthorityType.getByName(bf.readUtf());
            this.blockEntityExistence = BlockEntityExistence.read(bf);
        }

        public MusicPlayListChangeAuthorityMessage(UUID playlist, UUID player, AuthorityInfo.AuthorityType authorityType, BlockEntityExistence blockEntityExistence) {
            this.playlist = playlist;
            this.player = player;
            this.authorityType = authorityType;
            this.blockEntityExistence = blockEntityExistence;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(playlist);
            buf.writeUUID(player);
            buf.writeUtf(authorityType.getName());
            this.blockEntityExistence.write(buf);
            return buf;
        }
    }

    public static class MultipleMusicAddMessage implements PacketMessage {
        public final UUID playlist;
        public final List<Music> musics;
        public final BlockEntityExistence blockEntityExistence;

        public MultipleMusicAddMessage(FriendlyByteBuf bf) {
            this.playlist = bf.readUUID();
            this.musics = new ArrayList<>();
            IMPNbtUtil.readMusics(bf.readNbt(), "Musics", musics);
            this.blockEntityExistence = BlockEntityExistence.read(bf);
        }

        public MultipleMusicAddMessage(UUID playlist, List<Music> musics, BlockEntityExistence blockEntityExistence) {
            this.playlist = playlist;
            this.musics = musics;
            this.blockEntityExistence = blockEntityExistence;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(this.playlist);
            buf.writeNbt(IMPNbtUtil.writeMusics(new CompoundTag(), "Musics", musics));
            this.blockEntityExistence.write(buf);
            return buf;
        }
    }

    public static class MusicOrPlayListDeleteMessage implements PacketMessage {
        public final UUID playListID;
        public final UUID musicID;
        public final BlockEntityExistence blockEntityExistence;
        public final boolean music;

        public MusicOrPlayListDeleteMessage(FriendlyByteBuf bf) {
            this.playListID = bf.readUUID();
            this.musicID = bf.readUUID();
            this.blockEntityExistence = BlockEntityExistence.read(bf);
            this.music = bf.readBoolean();
        }

        public MusicOrPlayListDeleteMessage(UUID playListID, UUID musicID, BlockEntityExistence blockEntityExistence, boolean music) {
            this.playListID = playListID;
            this.musicID = musicID;
            this.blockEntityExistence = blockEntityExistence;
            this.music = music;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(this.playListID);
            buf.writeUUID(this.musicID);
            this.blockEntityExistence.write(buf);
            buf.writeBoolean(this.music);
            return buf;
        }
    }

    public static record MusicRingUpdateResultMessage(UUID uuid, UUID waitId,
                                                      MusicRingResponseStateType ringResponseStateType) implements PacketMessage {
        public MusicRingUpdateResultMessage(FriendlyByteBuf bf) {
            this(bf.readUUID(), bf.readUUID(), bf.readEnum(MusicRingResponseStateType.class));
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(uuid);
            buf.writeUUID(waitId);
            buf.writeEnum(ringResponseStateType);
            return buf;
        }
    }

/*
    public static class MusicRingUpdateResultMessage implements PacketMessage {
        public final UUID uuid;
        public final UUID waitId;
        public final int state;

        public MusicRingUpdateResultMessage(FriendlyByteBuf bf) {
            this.uuid = bf.readUUID();
            this.waitId = bf.readUUID();
            this.state = bf.readInt();

        }

        public MusicRingUpdateResultMessage(UUID uuid, UUID waitId, int state) {
            this.uuid = uuid;
            this.waitId = waitId;
            this.state = state;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(this.uuid);
            buf.writeUUID(this.waitId);
            buf.writeInt(this.state);
            return buf;
        }
    }*/

    public static record MusicRingStateMessage(UUID uuid, UUID waitId, MusicRingStateType stateType, long elapsed,
                                               CompoundTag tracker) implements PacketMessage {
        public MusicRingStateMessage(UUID uuid, UUID waitId, MusicRingStateType stateType) {
            this(uuid, waitId, stateType, 0, new CompoundTag());
        }

        public MusicRingStateMessage(FriendlyByteBuf bf) {
            this(bf.readUUID(), bf.readUUID(), bf.readEnum(MusicRingStateType.class), bf.readLong(), bf.readNbt());
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(uuid);
            buf.writeUUID(waitId);
            buf.writeEnum(stateType);
            buf.writeLong(elapsed);
            buf.writeNbt(tracker);
            return buf;
        }
    }

    public static class MusicRingReadyResultMessage implements PacketMessage {
        public final UUID waitID;
        public final UUID uuid;
        public final boolean result;
        public final boolean retry;
        public final long elapsed;

        public MusicRingReadyResultMessage(FriendlyByteBuf bf) {
            this.waitID = bf.readUUID();
            this.uuid = bf.readUUID();
            this.result = bf.readBoolean();
            this.retry = bf.readBoolean();
            this.elapsed = bf.readLong();
        }

        public MusicRingReadyResultMessage(UUID waitID, UUID uuid, boolean result, boolean retry, long elapsed) {
            this.waitID = waitID;
            this.uuid = uuid;
            this.result = result;
            this.retry = retry;
            this.elapsed = elapsed;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(waitID);
            buf.writeUUID(uuid);
            buf.writeBoolean(result);
            buf.writeBoolean(retry);
            buf.writeLong(elapsed);
            return buf;
        }
    }

    public static record MusicReadyMessage(UUID waitId, UUID uuid, MusicSource source, CompoundTag tracker,
                                           long position) implements PacketMessage {
        public MusicReadyMessage(FriendlyByteBuf bf) {
            this(bf.readUUID(), bf.readUUID(), TagSerializable.loadSavedTag(bf.readNbt(), new MusicSource()), bf.readNbt(), bf.readLong());
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(this.waitId);
            buf.writeUUID(this.uuid);
            buf.writeNbt(this.source.createSavedTag());
            buf.writeNbt(this.tracker);
            buf.writeLong(this.position);
            return buf;
        }
    }

    /*public static class MusicReadyMessage implements PacketMessage {
        public final UUID waitId;
        public final UUID uuid;
        public final MusicSource source;
        public final MusicTrackerEntry trackerEntry;
        public final long position;

        public MusicReadyMessage(FriendlyByteBuf bf) {
            this.waitId = bf.readUUID();
            this.uuid = bf.readUUID();
            this.source = TagSerializable.loadSavedTag(bf.readNbt().getCompound("ms"), new MusicSource());
            this.playbackInfo = TagSerializable.loadSavedTag(bf.readNbt().getCompound("pbi"), new MusicPlaybackInfo());
            this.position = bf.readLong();
        }

        public MusicReadyMessage(UUID waitId, UUID uuid, MusicSource source, MusicPlaybackInfo playbackInfo, long position) {
            this.waitId = waitId;
            this.uuid = uuid;
            this.source = source;
            this.playbackInfo = playbackInfo;
            this.position = position;
        }

        @Override
        public FriendlyByteBuf toFBB() {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeUUID(this.waitId);
            buf.writeUUID(this.uuid);
            var stag = new CompoundTag();
            stag.put("ms", this.source.createSavedTag());
            buf.writeNbt(stag);
            var ptag = new CompoundTag();
            ptag.put("pbi", this.playbackInfo.getTrackerTag());
            buf.writeNbt(ptag);
            buf.writeLong(this.position);
            return buf;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            return null;
        }
    }*/

    public static class MusicMessage implements PacketMessage {
        public final UUID uuid;
        public final UUID playlist;
        public final String name;
        public final String author;
        public final ImageInfo image;
        public final MusicSource source;
        public final BlockEntityExistence blockEntityExistence;

        public MusicMessage(FriendlyByteBuf bf) {
            this.uuid = bf.readUUID();
            this.playlist = bf.readUUID();
            this.name = bf.readUtf();
            this.author = bf.readUtf();
            this.image = TagSerializable.loadSavedTag(bf.readNbt().getCompound("Image"), new ImageInfo());
            this.source = TagSerializable.loadSavedTag(bf.readNbt().getCompound("Source"), new MusicSource());
            this.blockEntityExistence = BlockEntityExistence.read(bf);
        }

        public MusicMessage(UUID playlist, String name, String author, ImageInfo image, MusicSource source, BlockEntityExistence blockEntityExistence) {
            this(UUID.randomUUID(), playlist, name, author, image, source, blockEntityExistence);
        }

        public MusicMessage(UUID uuid, UUID playlist, String name, String author, ImageInfo image, MusicSource source, BlockEntityExistence blockEntityExistence) {
            this.uuid = uuid;
            this.playlist = playlist;
            this.name = name;
            this.author = author;
            this.image = image;
            this.source = source;
            this.blockEntityExistence = blockEntityExistence;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(this.uuid);
            buf.writeUUID(this.playlist);
            buf.writeUtf(this.name);
            buf.writeUtf(this.author);
            var itag = new CompoundTag();
            itag.put("Image", image.createSavedTag());
            buf.writeNbt(itag);
            var stag = new CompoundTag();
            stag.put("Source", source.createSavedTag());
            buf.writeNbt(stag);
            blockEntityExistence.write(buf);
            return buf;
        }
    }

    public static class MusicPlayListMessage implements PacketMessage {
        public final UUID uuid;
        public final String name;
        public final ImageInfo image;
        public final boolean publiced;
        public final boolean initMember;
        public final List<UUID> invitePlayers;
        public final BlockEntityExistence blockEntityExistence;
        public final List<Music> importMusics;

        public MusicPlayListMessage(FriendlyByteBuf bf) {
            this.uuid = bf.readUUID();
            this.name = bf.readUtf();
            this.image = TagSerializable.loadSavedTag(bf.readNbt().getCompound("Image"), new ImageInfo());
            this.publiced = bf.readBoolean();
            this.initMember = bf.readBoolean();
            this.invitePlayers = new ArrayList<>();
            OENbtUtils.readUUIDList(bf.readNbt(), "InvitePlayers", invitePlayers);
            this.blockEntityExistence = BlockEntityExistence.read(bf);
            this.importMusics = new ArrayList<>();
            IMPNbtUtil.readMusics(bf.readNbt(), "ImportMusics", importMusics);
        }

        public MusicPlayListMessage(String name, ImageInfo image, boolean publiced, boolean initMember, List<UUID> invitePlayers, BlockEntityExistence blockEntityExistence, List<Music> importMusics) {
            this(UUID.randomUUID(), name, image, publiced, initMember, invitePlayers, blockEntityExistence, importMusics);
        }

        public MusicPlayListMessage(UUID uuid, String name, ImageInfo image, boolean publiced, boolean initMember, List<UUID> invitePlayers, BlockEntityExistence blockEntityExistence, List<Music> importMusics) {
            this.uuid = uuid;
            this.name = name;
            this.image = image;
            this.publiced = publiced;
            this.initMember = initMember;
            this.invitePlayers = invitePlayers;
            this.blockEntityExistence = blockEntityExistence;
            this.importMusics = importMusics;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(this.uuid);
            buf.writeUtf(this.name);
            var tag = new CompoundTag();
            tag.put("Image", image.createSavedTag());
            buf.writeNbt(tag);
            buf.writeBoolean(publiced);
            buf.writeBoolean(initMember);
            buf.writeNbt(OENbtUtils.writeUUIDList(new CompoundTag(), "InvitePlayers", invitePlayers));
            blockEntityExistence.write(buf);
            buf.writeNbt(IMPNbtUtil.writeMusics(new CompoundTag(), "ImportMusics", importMusics));
            return buf;
        }
    }

    public static class MusicSyncResponseMessage implements PacketMessage {
        public final MusicSyncType syncType;
        public final UUID syncId;
        public final List<MusicPlayList> playLists;
        public final List<Music> musics;

        public MusicSyncResponseMessage(FriendlyByteBuf bf) {
            this.syncType = MusicSyncType.getById(bf.readInt());
            this.syncId = bf.readUUID();
            this.playLists = new ArrayList<>();
            IMPNbtUtil.readMusicPlayLists(bf.readNbt(), "PlayLists", playLists);
            this.musics = new ArrayList<>();
            IMPNbtUtil.readMusics(bf.readNbt(), "Musics", musics);
        }

        public MusicSyncResponseMessage(MusicSyncType syncType, UUID syncId, List<MusicPlayList> playLists, List<Music> musics) {
            this.syncType = syncType;
            this.syncId = syncId;
            this.playLists = playLists;
            this.musics = musics;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeInt(syncType.ordinal());
            buf.writeUUID(syncId);
            buf.writeNbt(IMPNbtUtil.writeMusicPlayLists(new CompoundTag(), "PlayLists", playLists));
            buf.writeNbt(IMPNbtUtil.writeMusics(new CompoundTag(), "Musics", musics));
            return buf;
        }
    }

    public static class MusicSyncRequestMessage implements PacketMessage {
        public final MusicSyncType syncType;
        public final UUID syncId;

        public MusicSyncRequestMessage(FriendlyByteBuf bf) {
            this(MusicSyncType.getById(bf.readInt()), bf.readUUID());
        }

        public MusicSyncRequestMessage(MusicSyncType syncType, UUID syncId) {
            this.syncType = syncType;
            this.syncId = syncId;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeInt(syncType.ordinal());
            buf.writeUUID(syncId);
            return buf;
        }
    }

    public static class WebDAVProfileSyncMessage implements PacketMessage {
        public final String baseUrl;
        public final String rootPath;
        public final String username;
        public final String password;

        public WebDAVProfileSyncMessage(FriendlyByteBuf buf) {
            this.baseUrl = buf.readUtf();
            this.rootPath = buf.readUtf();
            this.username = buf.readUtf();
            this.password = buf.readUtf();
        }

        public WebDAVProfileSyncMessage(String baseUrl, String rootPath, String username, String password) {
            this.baseUrl = baseUrl;
            this.rootPath = rootPath;
            this.username = username;
            this.password = password;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(baseUrl);
            buf.writeUtf(rootPath);
            buf.writeUtf(username);
            buf.writeUtf(password);
            return buf;
        }
    }

    public static class WebDAVProxyRequestMessage implements PacketMessage {
        public final UUID sessionId;

        public WebDAVProxyRequestMessage(FriendlyByteBuf buf) {
            this.sessionId = buf.readUUID();
        }

        public WebDAVProxyRequestMessage(UUID sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(sessionId);
            return buf;
        }
    }

    public static class WebDAVProxyStartMessage implements PacketMessage {
        public final UUID sessionId;
        public final long totalSize;
        public final String relativePath;

        public WebDAVProxyStartMessage(FriendlyByteBuf buf) {
            this.sessionId = buf.readUUID();
            this.totalSize = buf.readLong();
            this.relativePath = buf.readUtf();
        }

        public WebDAVProxyStartMessage(UUID sessionId, long totalSize, String relativePath) {
            this.sessionId = sessionId;
            this.totalSize = totalSize;
            this.relativePath = relativePath;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(sessionId);
            buf.writeLong(totalSize);
            buf.writeUtf(relativePath);
            return buf;
        }
    }

    public static class WebDAVProxyChunkMessage implements PacketMessage {
        public final UUID sessionId;
        public final byte[] chunk;

        public WebDAVProxyChunkMessage(FriendlyByteBuf buf) {
            this.sessionId = buf.readUUID();
            this.chunk = buf.readByteArray();
        }

        public WebDAVProxyChunkMessage(UUID sessionId, byte[] chunk) {
            this.sessionId = sessionId;
            this.chunk = chunk;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(sessionId);
            buf.writeByteArray(chunk);
            return buf;
        }
    }

    public static class WebDAVProxyEndMessage implements PacketMessage {
        public final UUID sessionId;

        public WebDAVProxyEndMessage(FriendlyByteBuf buf) {
            this.sessionId = buf.readUUID();
        }

        public WebDAVProxyEndMessage(UUID sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(sessionId);
            return buf;
        }
    }

    public static class WebDAVProxyErrorMessage implements PacketMessage {
        public final UUID sessionId;
        public final String error;

        public WebDAVProxyErrorMessage(FriendlyByteBuf buf) {
            this.sessionId = buf.readUUID();
            this.error = buf.readUtf();
        }

        public WebDAVProxyErrorMessage(UUID sessionId, String error) {
            this.sessionId = sessionId;
            this.error = error;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(sessionId);
            buf.writeUtf(error);
            return buf;
        }
    }

    public static enum MusicSyncType {
        NONE,
        PLAYLIST_CAN_JOIN,
        PLAYLIST_MY_LIST,
        MUSIC_BY_PLAYLIST,
        UPDATE;

        private static MusicSyncType getById(int id) {
            if (values().length > id)
                return values()[id];
            return NONE;
        }
    }

    public static enum MusicRingStateType {
        NONE,
        PLAY,
        STOP,
        UPDATE
    }

    public static enum MusicRingResponseStateType {
        NONE,
        PLAYING,
        LOADING
    }
}
