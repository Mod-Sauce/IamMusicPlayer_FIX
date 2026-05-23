package dev.felnull.imp.create.network;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import dev.architectury.registry.menu.MenuRegistry;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.block.IMPBaseEntityBlock;
import dev.felnull.imp.blockentity.BoomboxBlockEntity;
import dev.felnull.imp.create.behaviour.BoomboxMovementBehaviour;
import dev.felnull.imp.create.client.menu.MovingBoomboxMenu;
import dev.felnull.imp.create.container.MovingBoomboxContainer;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public record OpenBoomboxMenuPacket(int entityID,
                                    BlockPos pos
) implements CustomPacketPayload {
    public static final Type<OpenBoomboxMenuPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            IamMusicPlayer.MODID, "open_boombox_menu"
    ));
    public static final StreamCodec<ByteBuf, OpenBoomboxMenuPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            OpenBoomboxMenuPacket::entityID,
            BlockPos.STREAM_CODEC,
            OpenBoomboxMenuPacket::pos,
            OpenBoomboxMenuPacket::new
    );
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenBoomboxMenuPacket packet, NetworkManager.PacketContext packetContext) {
        var entity = packetContext.getPlayer().level().getEntity(packet.entityID());
        if(!(entity instanceof AbstractContraptionEntity contraptionEntity))return;
        var contraption = contraptionEntity.getContraption();
        var a = contraption.getActorAt(packet.pos);
        if(a == null)return;
        var content = a.getRight();
        var be = new BoomboxBlockEntity(content.localPos, content.state){
            @Override
            public boolean stillValid(@NotNull Player player) {
                return isUsableByPlayer(player);
            }

            @Override
            public boolean isUsableByPlayer(Player player) {
                var actor = contraption.getActorAt(packet.pos);
                if(actor == null)return false;
                return entity.isAlive() && player.distanceToSqr(
                        actor.right.position
                ) <= (double)64.0F;
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
        be.loadCustomOnly(content.blockEntityData, content.world.registryAccess());
        MenuRegistry.openExtendedMenu((ServerPlayer) packetContext.getPlayer(), new ExtendedMenuProvider() {
            @Override
            public void saveExtraData(FriendlyByteBuf buf) {
                buf.writeBoolean(false);
                buf.writeInt(be.getContainerSize());
                buf.writeBlockPos(content.localPos);
                buf.writeInt(entity.getId());
            }

            @Override
            public @NotNull Component getDisplayName() {
                return be.getDisplayName();
            }

            @Override
            public @NotNull AbstractContainerMenu createMenu(int i, Inventory arg, Player arg2) {
                return new MovingBoomboxMenu(i, arg, new MovingBoomboxContainer(be.getContainerSize(),
                        contraptionEntity, content.localPos),
                        content.localPos, ItemStack.EMPTY, null, entity.getId());
            }
        });
    }
}
