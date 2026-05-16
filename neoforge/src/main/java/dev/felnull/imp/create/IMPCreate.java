package dev.felnull.imp.create;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.block.IMPBlocks;
import dev.felnull.imp.blockentity.IMPBlockEntities;
import dev.felnull.imp.create.behaviour.BoomboxMovementBehaviour;
import dev.felnull.imp.create.behaviour.BoomboxMovingInteractionBehaviour;
import dev.felnull.imp.create.block.BoomboxControllerBlock;
import dev.felnull.imp.create.block_entity.BoomboxControllerBlockEntity;
import dev.felnull.imp.create.client.menu.BoomboxControllerMenu;
import dev.felnull.imp.create.client.menu.MovingBoomboxMenu;
import dev.felnull.imp.create.container.MovingBoomboxContainer;
import dev.felnull.imp.create.displaySource.BoomboxLyricSource;
import dev.felnull.imp.create.network.*;
import dev.felnull.imp.create.pointType.IMPPointTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.modsauce.otyacraftenginerenewed.util.OEMenuUtil;

public class IMPCreate {
    public static final CreateRegistrate REGISTRIES = CreateRegistrate.create(IamMusicPlayer.MODID);

    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(IamMusicPlayer.MODID, Registries.MENU);

    public static final RegistrySupplier<MenuType<MovingBoomboxMenu>> MOVING_BOOMBOX_MENU = MENUS.register(
            "moving_boombox_menu", () -> MenuRegistry.ofExtended((id, inventory, buf) -> {
                buf.readBoolean();
                var count = buf.readInt();
                var pos = buf.readBlockPos();
                var entityID = buf.readInt();
                return new MovingBoomboxMenu(id, inventory, MovingBoomboxContainer.create(count, inventory.player.level(), pos, entityID), pos, ItemStack.EMPTY, null, entityID);
            }));
    public static final RegistrySupplier<MenuType<BoomboxControllerMenu>> BOOMBOX_CONTROLLER_MENU = MENUS.register("boombox_controller", () -> OEMenuUtil.createMenuType(BoomboxControllerMenu::new));
    public static final BlockEntry<BoomboxControllerBlock> BOOMBOX_CONTROLLER_BLOCK = REGISTRIES.block("boombox_controller", BoomboxControllerBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .simpleItem()
            .register();
    public static final BlockEntityEntry<BoomboxControllerBlockEntity> BOOMBOX_CONTROLLER_BLOCK_ENTITY = REGISTRIES.blockEntity("boombox_controller", BoomboxControllerBlockEntity::new)
            .validBlock(BOOMBOX_CONTROLLER_BLOCK)
            .register();

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
        bus.addListener(IMPCreate::onRegistry);
        REGISTRIES.displaySource("boombox_lyric", BoomboxLyricSource::new)
                .onRegisterAfter(Registries.BLOCK_ENTITY_TYPE, boomboxLyricSource -> {
                    DisplaySource.BY_BLOCK_ENTITY.add(IMPBlockEntities.BOOMBOX.get(), boomboxLyricSource);
                })
                .register();
        REGISTRIES.registerEventListeners(bus);
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

    public static void onRegistry(RegisterEvent event){
        IMPPointTypes.init();
    }
}
