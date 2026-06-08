package dev.felnull.imp.forge.handler;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.blockentity.IMPBlockEntities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

@EventBusSubscriber(modid = IamMusicPlayer.MODID)
public class BECapabilityHandler {
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                IMPBlockEntities.BOOMBOX.get(),
                (be, d) -> new InvWrapper(be)
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                IMPBlockEntities.CASSETTE_DECK.get(),
                (be, d) -> new InvWrapper(be)
        );
    }
}
