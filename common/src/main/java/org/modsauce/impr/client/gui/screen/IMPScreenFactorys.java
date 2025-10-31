package org.modsauce.impr.client.gui.screen;

import dev.architectury.registry.menu.MenuRegistry;
import org.modsauce.impr.inventory.IMPMenus;

public class IMPScreenFactorys {
    public static void init() {
        MenuRegistry.registerScreenFactory(IMPMenus.MUSIC_MANAGER.get(), MusicManagerScreen::new);
        MenuRegistry.registerScreenFactory(IMPMenus.CASSETTE_DECK.get(), CassetteDeckScreen::new);
        MenuRegistry.registerScreenFactory(IMPMenus.BOOMBOX.get(), BoomboxScreen::new);
    }
}
