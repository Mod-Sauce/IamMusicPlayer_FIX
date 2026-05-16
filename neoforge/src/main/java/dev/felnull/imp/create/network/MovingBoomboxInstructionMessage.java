package dev.felnull.imp.create.network;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.block.BoomboxData;
import dev.felnull.imp.block.IMPBaseEntityBlock;
import dev.felnull.imp.blockentity.BoomboxBlockEntity;
import dev.felnull.imp.create.behaviour.BoomboxMovementBehaviour;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.server.music.ringer.IMusicRinger;
import dev.felnull.imp.server.music.ringer.MusicRingManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record MovingBoomboxInstructionMessage(
        UUID instructionScreenID,
        BlockPos localPos,
        int entityID,
        String name,
        CompoundTag data
) implements CustomPacketPayload {

    public static final Type<MovingBoomboxInstructionMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "moving_block_entity_instruction"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MovingBoomboxInstructionMessage> STREAM_CODEC =
            StreamCodec.of(
                    MovingBoomboxInstructionMessage::encode,
                    MovingBoomboxInstructionMessage::decode
            );

    public static void encode(RegistryFriendlyByteBuf buf, MovingBoomboxInstructionMessage msg) {
        buf.writeUUID(msg.instructionScreenID);
        buf.writeBlockPos(msg.localPos);
        buf.writeInt(msg.entityID);
        buf.writeUtf(msg.name);
        buf.writeNbt(msg.data);
    }

    public static MovingBoomboxInstructionMessage decode(RegistryFriendlyByteBuf buf) {
        return new MovingBoomboxInstructionMessage(
                buf.readUUID(),
                buf.readBlockPos(),
                buf.readInt(),
                buf.readUtf(),
                buf.readNbt()
        );
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(NetworkManager.PacketContext packetContext) {
        var entity = packetContext.getPlayer().level().getEntity(entityID());
        if(!(entity instanceof AbstractContraptionEntity contraptionEntity))return;
        var contraption = contraptionEntity.getContraption();
        var a = contraption.getActorAt(localPos);
        if(a == null)return;
        var content = a.getRight();
        var be = new BoomboxBlockEntity(localPos, content.state){
            @Override
            public void setPower(boolean on) {
                if (on != isPowered())
                    BoomboxMovementBehaviour.saveData(content, getBlockState().setValue(IMPBaseEntityBlock.POWERED, on));
            }

            @Override
            public void setChanged() {
                BoomboxMovementBehaviour.saveData(content, getBlockState().setValue(IMPBaseEntityBlock.POWERED, isPowered()),
                        saveWithoutMetadata(content.world.registryAccess()));
            }

            @Override
            public void updateLyric() {

            }
        };
        be.setBoomboxDataReally(new BoomboxData(null, new BoomboxData.DataAccess() {
            @Override
            public ItemStack getCassetteTape() {
                return be.getCassetteTape();
            }

            @Override
            public ItemStack getAntenna() {
                return be.getAntenna();
            }

            @Override
            public boolean isPowered() {
                return be.isPowered();
            }

            @Override
            public void setPower(boolean power) {
                be.setPower(power);
            }

            @Override
            public IMusicRinger getRinger() {
                if(content.blockEntityData.contains("RingerUUID") &&
                        MusicRingManager.getInstance().hasRinger(content.blockEntityData.getUUID("RingerUUID")))
                    return MusicRingManager.getInstance().getRinger(content.blockEntityData.getUUID("RingerUUID"));
                return be;
            }

            @Override
            public Vec3 getPosition() {
                return be.getSpatialPosition();
            }

            @Override
            public void setCassetteTape(ItemStack stack) {
                be.setItemNoUpdate(0, stack);
            }

            @Override
            public void dataUpdate(BoomboxData data) {
                be.setChanged();
            }
        }));
        be.loadCustomOnly(content.blockEntityData, entity.level().registryAccess());

        NetworkManager.sendToPlayer((ServerPlayer) packetContext.getPlayer(),
                new MovingBoomboxInstructionReturnMessage(
                instructionScreenID, localPos, entityID, name,
                be.onInstruction((ServerPlayer) packetContext.getPlayer(), name, data)
        ));
    }
}