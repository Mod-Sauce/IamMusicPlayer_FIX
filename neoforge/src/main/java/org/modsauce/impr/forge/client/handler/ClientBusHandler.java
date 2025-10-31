package org.modsauce.impr.forge.client.handler;

import org.modsauce.impr.IamMusicPlayer;
import org.modsauce.impr.client.IamMusicPlayerClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = IamMusicPlayer.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientBusHandler {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        IamMusicPlayerClient.init();
    }
}
