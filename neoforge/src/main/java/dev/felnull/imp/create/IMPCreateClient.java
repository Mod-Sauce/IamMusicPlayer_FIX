package dev.felnull.imp.create;

import dev.architectury.registry.menu.MenuRegistry;
import dev.felnull.imp.create.client.MovingBoomboxScreen;

public class IMPCreateClient {
    public static void init(){
        MenuRegistry.registerScreenFactory(IMPCreate.MOVING_BOOMBOX_MENU.get(), MovingBoomboxScreen::new);
    }
}
