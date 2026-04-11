package dev.felnull.imp.create;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.block.IMPBlocks;
import dev.felnull.imp.create.behaviour.BoomboxMovementBehaviour;
import dev.felnull.imp.create.behaviour.BoomboxMovingInteractionBehaviour;
import dev.felnull.imp.create.client.MovingBoomboxMenu;
import dev.felnull.imp.create.container.MovingBoomboxContainer;
import dev.felnull.imp.create.network.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

public class IMPCreate {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(IamMusicPlayer.MODID, Registries.MENU);
    public static final RegistrySupplier<MenuType<MovingBoomboxMenu>> MOVING_BOOMBOX_MENU = MENUS.register(
            "moving_boombox_menu", () -> MenuRegistry.ofExtended((id, inventory, buf) -> {
                buf.readBoolean();
                var count = buf.readInt();
                var pos = buf.readBlockPos();
                var entityID = buf.readInt();
                return new MovingBoomboxMenu(id, inventory, MovingBoomboxContainer.create(count, inventory.player.level(), pos, entityID), pos, ItemStack.EMPTY, null, entityID);
            }));
    public static void init(IEventBus bus){
        // bring Net Music: Advanced Player to IAM 🤔
        MENUS.register();
        bus.addListener(IMPCreate::setup);

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, ContraptionChangedPacketCTS.TYPE, ContraptionChangedPacketCTS.STREAM_CODEC, ContraptionChangedPacketCTS::handle);
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, OpenBoomboxMenuPacket.TYPE, OpenBoomboxMenuPacket.STREAM_CODEC, OpenBoomboxMenuPacket::handle);
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, MovingBoomboxInstructionMessage.TYPE, MovingBoomboxInstructionMessage.STREAM_CODEC, MovingBoomboxInstructionMessage::handle);
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, MovingBoomboxInstructionReturnMessage.TYPE, MovingBoomboxInstructionReturnMessage.STREAM_CODEC, MovingBoomboxInstructionReturnMessage::handle);
        if(Platform.getEnv().isClient())
            NetworkManager.registerReceiver(NetworkManager.Side.S2C, ContraptionChangedPacketSTC.TYPE, ContraptionChangedPacketSTC.STREAM_CODEC, ContraptionChangedPacketSTC::handle);
        else
            NetworkManager.registerS2CPayloadType(ContraptionChangedPacketSTC.TYPE, ContraptionChangedPacketSTC.STREAM_CODEC);
    }

    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            MovementBehaviour.REGISTRY.register(
                IMPBlocks.BOOMBOX.get(),
                new BoomboxMovementBehaviour()
            );
            MovingInteractionBehaviour.REGISTRY.register(
                IMPBlocks.BOOMBOX.get(),
                new BoomboxMovingInteractionBehaviour()
            );
        });
    }
}
