package dev.felnull.imp.forge.client.handler;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.IamMusicPlayerClient;
import dev.felnull.imp.client.gui.screen.BoomboxScreen;
import dev.felnull.imp.client.gui.screen.CassetteDeckScreen;
import dev.felnull.imp.client.gui.screen.MusicManagerScreen;
import dev.felnull.imp.create.IMPCreate;
import dev.felnull.imp.create.IMPCreateClient;
import dev.felnull.imp.create.client.screen.BoomboxControllerScreen;
import dev.felnull.imp.create.client.screen.MovingBoomboxScreen;
import dev.felnull.imp.integration.CreateIntegration;
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
        if(CreateIntegration.INSTANCE.isEnable())
            IMPCreateClient.init();
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(MUSIC_MANAGER.get(), MusicManagerScreen::new);
        event.register(CASSETTE_DECK.get(), CassetteDeckScreen::new);
        event.register(BOOMBOX.get(), BoomboxScreen::new);

        if(CreateIntegration.INSTANCE.isEnable()) {
            event.register(IMPCreate.MOVING_BOOMBOX_MENU.get(), MovingBoomboxScreen::new);
            event.register(IMPCreate.BOOMBOX_CONTROLLER_MENU.get(), BoomboxControllerScreen::new);
        }
    }
}
