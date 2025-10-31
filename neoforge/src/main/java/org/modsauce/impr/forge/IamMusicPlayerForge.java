package org.modsauce.impr.forge;

import dev.architectury.platform.forge.EventBuses;
import org.modsauce.impr.IamMusicPlayer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@SuppressWarnings("Deprecated")
@Mod(IamMusicPlayer.MODID)
public class IamMusicPlayerForge {

  public IamMusicPlayerForge() {
    EventBuses.registerModEventBus(
      IamMusicPlayer.MODID,
      FMLJavaModLoadingContext.get().getModEventBus()
    );
    IamMusicPlayer.init();
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
  }

  private void setup(FMLCommonSetupEvent e) {
    IamMusicPlayer.setup();
  }
}
