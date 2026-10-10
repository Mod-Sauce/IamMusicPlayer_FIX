package dev.felnull.imp.networking;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.handler.ClientMessageHandler;
import dev.felnull.imp.music.resource.*;
import dev.felnull.imp.server.handler.ServerMessageHandler;
import dev.felnull.imp.util.IMPNbtUtil;
import io.netty.handler.codec.DecoderException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class IMPPackets {
    /**
     * Reads a client-supplied element count and rejects values that cannot be honest. The decoder runs on the network
     * thread before any handler sees the packet, and the count is used to size a list, so a forged value would
     * otherwise be enough to disconnect the player or exhaust the heap (see {@link MusicDeleteBatchMessage#MAX_BATCH}).
     * {@code n} is also never larger than the remaining bytes: every entry costs at least one byte on the wire.
     */
    private static int readCount(FriendlyByteBuf bf, String what, int max) {
        int n = bf.readVarInt();
        if (n < 0 || n > max || n > bf.readableBytes())
            throw new DecoderException(what + " size: " + n);
        return n;
    }

    public static final ResourceLocation MUSIC_SYNC_STC = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_sync_stc");
    public static final ResourceLocation MUSIC_SYNC_CTS = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_sync_cts");
    public static final ResourceLocation MUSIC_PLAYLIST_ADD = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_playlist_add");
    public static final ResourceLocation MUSIC_PLAYLIST_EDIT = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_playlist_edit");
    /**
     * Writes only the import source (and the cover when one is sent); **not** {@code MUSIC_PLAYLIST_EDIT} — that one rebuilds the whole authority from the client's data.
     */
    public static final ResourceLocation MUSIC_PLAYLIST_SOURCE = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_playlist_source");
    public static final ResourceLocation MUSIC_PLAYLIST_CHANGE_AUTHORITY = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_playlist_change_authority");
    public static final ResourceLocation MUSIC_ADD = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_add");
    public static final ResourceLocation MUSIC_EDIT = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_edit");
    public static final ResourceLocation MUSIC_OR_PLAYLIST_DELETE = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_or_playlist_delete");
    /** Batch music deletion (used by the music management page): carries multiple musicIds at once, the server judges each one by authority. */
    public static final ResourceLocation MUSIC_DELETE_BATCH = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_delete_batch");
    /** Undo the last batch deletion: carries the deleted songs' uuids; the server restores the content from its own copy (the 6-second undo on the music management page). */
    public static final ResourceLocation MUSIC_DELETE_BATCH_UNDO = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_delete_batch_undo");
    public static final ResourceLocation MUSIC_RING_READY = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_ring_ready");
    public static final ResourceLocation MUSIC_RING_READY_RESULT = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_ring_ready_result");
    public static final ResourceLocation MUSIC_RING_STATE = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_ring_state");
    public static final ResourceLocation MUSIC_RING_UPDATE_RESULT = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_ring_update_result");
    public static final ResourceLocation MULTIPLE_MUSIC_ADD = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "multiple_music_add");
    /**
     * The client hands the server the part list it learned for one B站 video, so the boombox can continue with the next part without that part being a playlist entry.
     */
    public static final ResourceLocation BILIBILI_PARTS = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "bilibili_parts");
    /** 客户端上报 B站 投稿时间，服务端拿它排「投稿日期」那一档。 */
    public static final ResourceLocation BILIBILI_PUBDATES = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "bilibili_pubdates");
    /**
     * 客户端上报要跳掉的赞助/广告片段（源时间毫秒）：服务端只用它算**内容实际长度**，音响时间轴按内容走（否则曲尾留一段静音）。
     */
    public static final ResourceLocation BILIBILI_SPONSOR = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "bilibili_sponsor");
    /**
     * The listener asks the boombox playing for them to jump to part N of the video it is on; only the part number travels, the server resolves which boombox is meant.
     */
    public static final ResourceLocation BOOMBOX_PART_JUMP = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "boombox_part_jump");
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
        NetworkManager.registerReceiver(NetworkManager.c2s(), MUSIC_PLAYLIST_SOURCE, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onMusicPlayListSourceMessage(new PlayListSourceMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), MUSIC_PLAYLIST_CHANGE_AUTHORITY, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onMusicPlayListChangeAuthority(new MusicPlayListChangeAuthorityMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), MUSIC_ADD, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onMusicAddMessage(new MusicMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), MUSIC_EDIT, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onMusicEditMessage(new MusicMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), MUSIC_RING_READY_RESULT, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onMusicReadyResultMessage(new MusicRingReadyResultMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), MUSIC_RING_UPDATE_RESULT, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onMusicUpdateResultMessage(new MusicRingUpdateResultMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), MUSIC_OR_PLAYLIST_DELETE, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onMusicOrPlayListDeleteMessage(new MusicOrPlayListDeleteMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), MUSIC_DELETE_BATCH, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onMusicDeleteBatchMessage(new MusicDeleteBatchMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), MUSIC_DELETE_BATCH_UNDO, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onMusicDeleteBatchUndoMessage(new MusicDeleteUndoMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), MULTIPLE_MUSIC_ADD, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onMultipleMusicAdd(new MultipleMusicAddMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), BILIBILI_PARTS, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onBiliBiliPartsMessage(new BiliBiliPartsMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), BILIBILI_PUBDATES, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onBiliBiliPubdatesMessage(new BiliBiliPubdatesMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), BILIBILI_SPONSOR, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onBiliBiliSponsorMessage(new BiliBiliSponsorMessage(friendlyByteBuf), packetContext));
        NetworkManager.registerReceiver(NetworkManager.c2s(), BOOMBOX_PART_JUMP, (friendlyByteBuf, packetContext) -> ServerMessageHandler.onBoomboxPartJumpMessage(new BoomboxPartJumpMessage(friendlyByteBuf), packetContext));
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
        /**
         * Optional, one entry per music (empty = append everything): what each song is inserted directly behind — the 分P parts belong right behind their video.
         */
        public final List<UUID> afterMusics;
        public final BlockEntityExistence blockEntityExistence;

        public MultipleMusicAddMessage(FriendlyByteBuf bf) {
            this.playlist = bf.readUUID();
            this.musics = new ArrayList<>();
            IMPNbtUtil.readMusics(bf.readNbt(), "Musics", musics);
            this.afterMusics = new ArrayList<>();
            if (bf.readBoolean()) {
                int n = bf.readVarInt();
                for (int i = 0; i < n; i++)
                    this.afterMusics.add(bf.readUUID());
            }
            this.blockEntityExistence = BlockEntityExistence.read(bf);
        }

        public MultipleMusicAddMessage(UUID playlist, List<Music> musics, BlockEntityExistence blockEntityExistence) {
            this(playlist, musics, List.of(), blockEntityExistence);
        }

        public MultipleMusicAddMessage(UUID playlist, List<Music> musics, List<UUID> afterMusics,
                                       BlockEntityExistence blockEntityExistence) {
            this.playlist = playlist;
            this.musics = musics;
            this.afterMusics = afterMusics == null ? List.of() : afterMusics;
            this.blockEntityExistence = blockEntityExistence;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(this.playlist);
            buf.writeNbt(IMPNbtUtil.writeMusics(new CompoundTag(), "Musics", musics));
            // Written in the same order both sides read it: a flag, then the count, then the uuids (nothing at all
            // when the list is empty — the import path keeps sending exactly the bytes it used to)
            buf.writeBoolean(!afterMusics.isEmpty());
            if (!afterMusics.isEmpty()) {
                buf.writeVarInt(afterMusics.size());
                for (var id : afterMusics)
                    buf.writeUUID(id);
            }
            this.blockEntityExistence.write(buf);
            return buf;
        }
    }

    /**
     * One B站 video's part list (client → server, once per video; only the client can ask B站): parallel name/length lists, an empty name = labelled by its number.
     */
    public static class BiliBiliPartsMessage implements PacketMessage {
        public final String bvid;
        public final List<String> names;
        public final List<Long> durations;
        /** Cap on the parts one video may report; the count sizes two lists on the server side. */
        public static final int MAX_PARTS = 1024;

        public BiliBiliPartsMessage(FriendlyByteBuf bf) {
            this.bvid = bf.readUtf();
            int n = readCount(bf, "bilibili parts", MAX_PARTS);
            this.names = new ArrayList<>(n);
            this.durations = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                this.names.add(bf.readUtf());
                this.durations.add(bf.readLong());
            }
        }

        public BiliBiliPartsMessage(String bvid, List<String> names, List<Long> durations) {
            this.bvid = bvid;
            this.names = names;
            this.durations = durations;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(this.bvid == null ? "" : this.bvid);
            buf.writeVarInt(this.names.size());
            for (int i = 0; i < this.names.size(); i++) {
                buf.writeUtf(this.names.get(i) == null ? "" : this.names.get(i));
                buf.writeLong(i < this.durations.size() && this.durations.get(i) != null ? this.durations.get(i) : 0L);
            }
            return buf;
        }
    }

    /**
     * B站 赞助/广告片段上报（客户端 → 服务端）：起止时间用平行列表（源时间毫秒）；标识符带分P（{@code BV…-pN}），空表 = 清掉这条记录。
     */
    public static class BiliBiliSponsorMessage implements PacketMessage {
        public final String identifier;
        public final List<Long> starts;
        public final List<Long> ends;
        /** Cap on the segments one track may report; the count sizes two lists on the server side. */
        public static final int MAX_SEGMENTS = 4096;

        public BiliBiliSponsorMessage(FriendlyByteBuf bf) {
            this.identifier = bf.readUtf();
            int n = readCount(bf, "bilibili sponsor segments", MAX_SEGMENTS);
            this.starts = new ArrayList<>(n);
            this.ends = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                this.starts.add(bf.readLong());
                this.ends.add(bf.readLong());
            }
        }

        public BiliBiliSponsorMessage(String identifier, List<Long> starts, List<Long> ends) {
            this.identifier = identifier;
            this.starts = starts;
            this.ends = ends;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(this.identifier == null ? "" : this.identifier);
            buf.writeVarInt(this.starts.size());
            for (int i = 0; i < this.starts.size(); i++) {
                buf.writeLong(this.starts.get(i) == null ? 0L : this.starts.get(i));
                buf.writeLong(i < this.ends.size() && this.ends.get(i) != null ? this.ends.get(i) : 0L);
            }
            return buf;
        }
    }

    /**
     * B站 投稿时间批量上报（客户端 → 服务端）：服务端排序要用，只有客户端拿得到；bvids 与 pubdates 平行列表，重复报同一 bvid 直接覆盖。
     */
    public static class BiliBiliPubdatesMessage implements PacketMessage {
        public final List<String> bvids;
        public final List<Long> pubdates;
        /** Cap on the entries one report may carry; the count sizes two lists on the server side. */
        public static final int MAX_ENTRIES = 4096;

        public BiliBiliPubdatesMessage(FriendlyByteBuf bf) {
            int n = readCount(bf, "bilibili pubdates", MAX_ENTRIES);
            this.bvids = new ArrayList<>(n);
            this.pubdates = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                this.bvids.add(bf.readUtf());
                this.pubdates.add(bf.readLong());
            }
        }

        public BiliBiliPubdatesMessage(List<String> bvids, List<Long> pubdates) {
            this.bvids = bvids;
            this.pubdates = pubdates;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(this.bvids.size());
            for (int i = 0; i < this.bvids.size(); i++) {
                buf.writeUtf(this.bvids.get(i) == null ? "" : this.bvids.get(i));
                buf.writeLong(i < this.pubdates.size() && this.pubdates.get(i) != null ? this.pubdates.get(i) : 0L);
            }
            return buf;
        }
    }

    /**
     * Jump to one part of the B站 video a boombox is playing: only the part number travels, the server derives which boombox the player hears (the client cannot know).
     */
    public static class BoomboxPartJumpMessage implements PacketMessage {
        /** 1-based part number the listener typed. */
        public final int page;

        public BoomboxPartJumpMessage(FriendlyByteBuf bf) {
            this.page = bf.readVarInt();
        }

        public BoomboxPartJumpMessage(int page) {
            this.page = page;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(this.page);
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

    /**
     * Batch music deletion (music management page).
     */
    public static class MusicDeleteBatchMessage implements PacketMessage {
        /**
         * Max musics one batch may delete. The cap must really block: {@code n} comes from the client, so sizing an {@code ArrayList} with it would let a forged n crash the connection.
         */
        public static final int MAX_BATCH = 4096;

        public final UUID playListID;
        public final List<UUID> musicIDs;
        public final BlockEntityExistence blockEntityExistence;

        public MusicDeleteBatchMessage(FriendlyByteBuf bf) {
            this.playListID = bf.readUUID();
            int n = bf.readInt();
            if (n < 0 || n > MAX_BATCH)
                throw new DecoderException("music delete batch size: " + n);
            var ids = new ArrayList<UUID>(n);
            for (int i = 0; i < n; i++)
                ids.add(bf.readUUID());
            this.musicIDs = ids;
            this.blockEntityExistence = BlockEntityExistence.read(bf);
        }

        public MusicDeleteBatchMessage(UUID playListID, List<UUID> musicIDs, BlockEntityExistence blockEntityExistence) {
            this.playListID = playListID;
            this.musicIDs = musicIDs;
            this.blockEntityExistence = blockEntityExistence;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(this.playListID);
            buf.writeInt(this.musicIDs.size());
            for (UUID id : this.musicIDs)
                buf.writeUUID(id);
            this.blockEntityExistence.write(buf);
            return buf;
        }
    }

    /**
     * Undo the last batch deletion: the sent music objects are used as a uuid list only, the content is restored from the server's own copy, so a forged object injects nothing.
     */
    public static class MusicDeleteUndoMessage implements PacketMessage {
        public final UUID playListID;
        public final List<Music> musics;
        public final BlockEntityExistence blockEntityExistence;

        public MusicDeleteUndoMessage(FriendlyByteBuf bf) {
            this.playListID = bf.readUUID();
            this.musics = new ArrayList<>();
            // readNbt() returns null on an EndTag (empty compound) ⇒ guard before reading, otherwise
            // OENbtUtils.readList dereferences null (same convention as MusicSyncResponseMessage below).
            var musicsTag = bf.readNbt();
            if (musicsTag != null) IMPNbtUtil.readMusics(musicsTag, "Musics", musics);
            // Cap the decoded batch like MusicDeleteBatchMessage: this list comes from the client and is
            // written into save data, so an oversized one is rejected instead of stored.
            if (this.musics.size() > MusicDeleteBatchMessage.MAX_BATCH)
                throw new DecoderException("music delete undo size: " + this.musics.size());
            this.blockEntityExistence = BlockEntityExistence.read(bf);
        }

        public MusicDeleteUndoMessage(UUID playListID, List<Music> musics, BlockEntityExistence blockEntityExistence) {
            this.playListID = playListID;
            this.musics = musics;
            this.blockEntityExistence = blockEntityExistence;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(this.playListID);
            buf.writeNbt(IMPNbtUtil.writeMusics(new CompoundTag(), "Musics", musics));
            this.blockEntityExistence.write(buf);
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
        /**
         * Import source of a **new** playlist ({@code space:<mid>} / {@code fav:<fid>} / empty) — travels last; {@code MUSIC_PLAYLIST_EDIT} ignores it.
         */
        public final String source;

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
            // The import source travels last and only exists in newer builds: an older client ends here, so absent
            // bytes mean "no source" rather than a decode failure that would kick the player.
            this.source = bf.readableBytes() > 0 ? bf.readUtf() : "";
        }

        public MusicPlayListMessage(String name, ImageInfo image, boolean publiced, boolean initMember, List<UUID> invitePlayers, BlockEntityExistence blockEntityExistence, List<Music> importMusics) {
            this(name, image, publiced, initMember, invitePlayers, blockEntityExistence, importMusics, "");
        }

        /** The import path: same as above plus the source the created playlist should remember. */
        public MusicPlayListMessage(String name, ImageInfo image, boolean publiced, boolean initMember, List<UUID> invitePlayers, BlockEntityExistence blockEntityExistence, List<Music> importMusics, String source) {
            this(UUID.randomUUID(), name, image, publiced, initMember, invitePlayers, blockEntityExistence, importMusics, source);
        }

        public MusicPlayListMessage(UUID uuid, String name, ImageInfo image, boolean publiced, boolean initMember, List<UUID> invitePlayers, BlockEntityExistence blockEntityExistence, List<Music> importMusics) {
            this(uuid, name, image, publiced, initMember, invitePlayers, blockEntityExistence, importMusics, "");
        }

        /**
         * The import path with a **client-chosen** id (a multi-packet import appends the rest to it); the server refuses a uuid that is already taken.
         */
        public MusicPlayListMessage(UUID uuid, String name, ImageInfo image, boolean publiced, boolean initMember, List<UUID> invitePlayers, BlockEntityExistence blockEntityExistence, List<Music> importMusics, String source) {
            this.uuid = uuid;
            this.name = name;
            this.image = image;
            this.publiced = publiced;
            this.initMember = initMember;
            this.invitePlayers = invitePlayers;
            this.blockEntityExistence = blockEntityExistence;
            this.importMusics = importMusics;
            this.source = source == null ? "" : source;
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
            buf.writeUtf(this.source == null ? "" : this.source);
            return buf;
        }
    }

    /**
     * 「刷新」 bookkeeping: remember/correct the import source, optionally a fresh cover and a rename (legacy names are **stored data**, so fixing the generator cannot touch them).
     */
    public static class PlayListSourceMessage implements PacketMessage {
        public final UUID uuid;
        public final String source;
        public final ImageInfo image;
        /** Optional new name; empty = keep the current one. */
        public final String name;
        public final BlockEntityExistence blockEntityExistence;

        public PlayListSourceMessage(FriendlyByteBuf bf) {
            this.uuid = bf.readUUID();
            this.source = bf.readUtf();
            this.image = TagSerializable.loadSavedTag(bf.readNbt().getCompound("Image"), new ImageInfo());
            this.name = bf.readUtf();
            this.blockEntityExistence = BlockEntityExistence.read(bf);
        }

        public PlayListSourceMessage(UUID uuid, String source, ImageInfo image, BlockEntityExistence blockEntityExistence) {
            this(uuid, source, image, "", blockEntityExistence);
        }

        public PlayListSourceMessage(UUID uuid, String source, ImageInfo image, String name, BlockEntityExistence blockEntityExistence) {
            this.uuid = uuid;
            this.source = source == null ? "" : source;
            this.image = image == null ? ImageInfo.EMPTY : image;
            this.name = name == null ? "" : name;
            this.blockEntityExistence = blockEntityExistence;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(this.uuid);
            buf.writeUtf(this.source);
            var tag = new CompoundTag();
            tag.put("Image", image.createSavedTag());
            buf.writeNbt(tag);
            buf.writeUtf(this.name);
            blockEntityExistence.write(buf);
            return buf;
        }
    }

    public static class MusicSyncResponseMessage implements PacketMessage {
        public final MusicSyncType syncType;
        public final UUID syncId;
        public final List<MusicPlayList> playLists;
        public final List<Music> musics;
        /**
         * Server-sent names: a **flat name map** (UUID → display name, see {@code IMPNbtUtil#writePlayerNames}).
         */
        public final Map<UUID, String> playerNames;
        /** Index of the first song in {@link #musics} and how many the playlist holds in total (paged musics only). */
        public final int musicOffset;
        public final int musicTotal;

        public MusicSyncResponseMessage(FriendlyByteBuf bf) {
            this.syncType = MusicSyncType.getById(bf.readInt());
            this.syncId = bf.readUUID();
            this.playLists = new ArrayList<>();
            IMPNbtUtil.readMusicPlayLists(bf.readNbt(), "PlayLists", playLists);
            this.musics = new ArrayList<>();
            IMPNbtUtil.readMusics(bf.readNbt(), "Musics", musics);
            this.playerNames = new LinkedHashMap<>();
            // Fallback: packets from an old server (without this name map) have no bytes left here ⇒ check isReadable first,
            // so readNbt() does not throw IndexOutOfBounds and kick the player off the server.
            if (bf.isReadable()) {
                var namesTag = bf.readNbt();
                if (namesTag != null) IMPNbtUtil.readPlayerNames(namesTag, "PlayerNames", playerNames);
            }
            this.musicOffset = bf.readableBytes() >= 4 ? bf.readInt() : 0;
            this.musicTotal = bf.readableBytes() >= 4 ? bf.readInt() : 0;
            applyPlayerNames();
        }

        public MusicSyncResponseMessage(MusicSyncType syncType, UUID syncId, List<MusicPlayList> playLists, List<Music> musics, Map<UUID, String> playerNames) {
            this(syncType, syncId, playLists, musics, playerNames, 0, 0);
        }

        public MusicSyncResponseMessage(MusicSyncType syncType, UUID syncId, List<MusicPlayList> playLists, List<Music> musics, Map<UUID, String> playerNames, int musicOffset, int musicTotal) {
            this.syncType = syncType;
            this.syncId = syncId;
            this.playLists = playLists;
            this.musics = musics;
            this.playerNames = playerNames;
            this.musicOffset = musicOffset;
            this.musicTotal = musicTotal;
        }

        /**
         * Attaches the name map back onto the objects just received; an empty map returns immediately, so old peers are unaffected.
         */
        private void applyPlayerNames() {
            if (playerNames == null || playerNames.isEmpty()) return;
            for (var playList : playLists) {
                if (playList != null && playList.getAuthority() != null)
                    playList.getAuthority().setMemberNames(playerNames);
            }
            for (var music : musics) {
                if (music != null && music.getOwner() != null)
                    music.setOwnerName(playerNames.get(music.getOwner()));
            }
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeInt(syncType.ordinal());
            buf.writeUUID(syncId);
            buf.writeNbt(IMPNbtUtil.writeMusicPlayLists(new CompoundTag(), "PlayLists", playLists));
            buf.writeNbt(IMPNbtUtil.writeMusics(new CompoundTag(), "Musics", musics));
            buf.writeNbt(IMPNbtUtil.writePlayerNames(new CompoundTag(), "PlayerNames", playerNames));
            buf.writeInt(musicOffset);
            buf.writeInt(musicTotal);
            return buf;
        }
    }

    public static class MusicSyncRequestMessage implements PacketMessage {
        public final MusicSyncType syncType;
        public final UUID syncId;
        /**
         * First song to send ({@code MUSIC_BY_PLAYLIST} only): one packet cannot carry an unbounded list ({@code IMPNbtUtil#PACKET_NBT_BUDGET}), so the client pages through.
         */
        public final int offset;

        public MusicSyncRequestMessage(FriendlyByteBuf bf) {
            this(MusicSyncType.getById(bf.readInt()), bf.readUUID(), bf.readableBytes() >= 4 ? bf.readInt() : 0);
        }

        public MusicSyncRequestMessage(MusicSyncType syncType, UUID syncId) {
            this(syncType, syncId, 0);
        }

        public MusicSyncRequestMessage(MusicSyncType syncType, UUID syncId, int offset) {
            this.syncType = syncType;
            this.syncId = syncId;
            this.offset = offset;
        }

        @Override
        public RegistryFriendlyByteBuf toRFBB(RegistryFriendlyByteBuf buf) {
            buf.writeInt(syncType.ordinal());
            buf.writeUUID(syncId);
            buf.writeInt(offset);
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
        UPDATE,
        /**
         * Playlists I was invited to (not accepted yet). Must stay last: {@code syncType} travels by {@code ordinal()}.
         */
        PLAYLIST_INVITES;

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
