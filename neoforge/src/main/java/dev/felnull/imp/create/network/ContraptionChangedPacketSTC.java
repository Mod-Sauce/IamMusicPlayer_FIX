// Thanks Kaleidoscope Contraption
package dev.felnull.imp.create.network;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.IamMusicPlayer;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.NotNull;

public record ContraptionChangedPacketSTC(int entityID,
                                          BlockPos pos,
                                          BlockState state,
                                          CompoundTag data
) implements CustomPacketPayload {
    public static final Type<ContraptionChangedPacketSTC> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            IamMusicPlayer.MODID, "contraption_changed_stc"
    ));
    public static final StreamCodec<ByteBuf, ContraptionChangedPacketSTC> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            ContraptionChangedPacketSTC::entityID,
            BlockPos.STREAM_CODEC,
            ContraptionChangedPacketSTC::pos,
            ByteBufCodecs.fromCodec(BlockState.CODEC),
            ContraptionChangedPacketSTC::state,
            ByteBufCodecs.COMPOUND_TAG,
            ContraptionChangedPacketSTC::data,
            ContraptionChangedPacketSTC::new
    );
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ContraptionChangedPacketSTC packet, NetworkManager.PacketContext packetContext) {
        var entity = packetContext.getPlayer().level().getEntity(packet.entityID());
        if(!(entity instanceof AbstractContraptionEntity contraptionEntity))return;
        var contraption = contraptionEntity.getContraption();
        var a = contraption.getActorAt(packet.pos);
        if(a == null)return;
        a.right.blockEntityData = packet.data;
        var r = contraption.getBlocks().remove(packet.pos);
        contraption.getBlocks().put(packet.pos, new StructureTemplate.StructureBlockInfo(r.pos(), packet.state, packet.data));
        var clientContraption = contraptionEntity.getContraption().getOrCreateClientContraptionLazy();
        var blockEntity = clientContraption.getBlockEntity(packet.pos);
        if (blockEntity != null) {
            blockEntity.loadCustomOnly(packet.data, entity.level().registryAccess());
            blockEntity.setBlockState(packet.state);
        }
    }
}
