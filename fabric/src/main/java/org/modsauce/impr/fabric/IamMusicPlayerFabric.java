package org.modsauce.impr.fabric;

import org.modsauce.impr.IamMusicPlayer;
import net.fabricmc.api.ModInitializer;

public class IamMusicPlayerFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        IamMusicPlayer.init();
        IamMusicPlayer.setup();
    }
}
