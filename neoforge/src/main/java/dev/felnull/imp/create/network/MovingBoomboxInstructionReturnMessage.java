package dev.felnull.imp.create.network;

import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.IamMusicPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.modsauce.otyacraftenginerenewed.client.gui.screen.InstructionBEScreen;

import java.util.UUID;

public record MovingBoomboxInstructionReturnMessage(
        UUID instructionScreenID,
        BlockPos localPos,
        int entityID,
        String name,
        CompoundTag data
) implements CustomPacketPayload {

    public static final Type<MovingBoomboxInstructionReturnMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "moving_block_entity_instruction_return"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MovingBoomboxInstructionReturnMessage> STREAM_CODEC =
            StreamCodec.of(
                    MovingBoomboxInstructionReturnMessage::encode,
                    MovingBoomboxInstructionReturnMessage::decode
            );

    public static void encode(RegistryFriendlyByteBuf buf, MovingBoomboxInstructionReturnMessage msg) {
        buf.writeUUID(msg.instructionScreenID);
        buf.writeBlockPos(msg.localPos);
        buf.writeInt(msg.entityID);
        buf.writeUtf(msg.name);
        buf.writeNbt(msg.data);
    }

    public static MovingBoomboxInstructionReturnMessage decode(RegistryFriendlyByteBuf buf) {
        return new MovingBoomboxInstructionReturnMessage(
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
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof InstructionBEScreen insScreen && insScreen.getInstructionID().equals(instructionScreenID()))
            insScreen.onInstructionReturn(name(), data());
    }
}