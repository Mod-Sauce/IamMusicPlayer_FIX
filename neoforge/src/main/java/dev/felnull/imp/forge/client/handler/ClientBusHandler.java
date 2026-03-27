package dev.felnull.imp.forge.client.handler;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.IamMusicPlayerClient;
import dev.felnull.imp.client.gui.screen.BoomboxScreen;
import dev.felnull.imp.client.gui.screen.CassetteDeckScreen;
import dev.felnull.imp.client.gui.screen.MusicManagerScreen;
import dev.felnull.imp.inventory.IMPMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import static dev.felnull.imp.inventory.IMPMenus.*;

@EventBusSubscriber(modid = IamMusicPlayer.MODID, value = Dist.CLIENT)
public class ClientBusHandler {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        IamMusicPlayerClient.init();
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(MUSIC_MANAGER.get(), MusicManagerScreen::new);
        event.register(CASSETTE_DECK.get(), CassetteDeckScreen::new);
        event.register(BOOMBOX.get(), BoomboxScreen::new);
    }
}
