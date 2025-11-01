package org.modsauce.impr.fabric.client;

import org.modsauce.impr.IamMusicPlayer;
import org.modsauce.impr.client.IamMusicPlayerClient;
import dev.felnull.specialmodelloader.api.event.SpecialModelLoaderEvents;
import net.fabricmc.api.ClientModInitializer;

public class IamMusicPlayerClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        IamMusicPlayerClient.init();
        // TODO: Fix for special-model-loader 1.3.0 - API changed, need to investigate new API
        // SpecialModelLoaderEvents.LOAD_SCOPE.register(loc -> IamMusicPlayer.MODID.equals(loc.getNamespace()));
    }
}
