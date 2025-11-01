package org.modsauce.impr.forge.client.handler;

import org.modsauce.impr.IamMusicPlayer;
import org.modsauce.impr.client.IamMusicPlayerClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = IamMusicPlayer.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientBusHandler {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        IamMusicPlayerClient.init();
    }
}
