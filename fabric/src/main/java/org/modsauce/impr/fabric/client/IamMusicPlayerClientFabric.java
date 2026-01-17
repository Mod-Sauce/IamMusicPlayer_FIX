package org.modsauce.impr.fabric.client;

import dev.felnull.specialmodelloader.api.event.SpecialModelLoaderEvents;
import net.fabricmc.api.ClientModInitializer;
import org.modsauce.impr.IamMusicPlayer;
import org.modsauce.impr.client.IamMusicPlayerClient;

public class IamMusicPlayerClientFabric implements ClientModInitializer {

  @Override
  public void onInitializeClient() {
    IamMusicPlayerClient.init();
    // NOTE: special-model-loader 1.3.0 changed its API and the previous `LOAD_SCOPE` registration is no longer available.
    // Investigate the new API and implement conditional/optional registration so the mod remains compatible when the
    // library is present. Avoid hard dependency on the loader by using reflection or an optional integration hook.
    // The previous usage is kept commented for reference:
    // SpecialModelLoaderEvents.LOAD_SCOPE.register(loc -> IamMusicPlayer.MODID.equals(loc.getNamespace()));
  }
}
