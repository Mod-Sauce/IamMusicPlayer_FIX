package org.modsauce.impr.forge;

import org.modsauce.impr.IamMusicPlayer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

@Mod(IamMusicPlayer.MODID)
public class IamMusicPlayerForge {

  public IamMusicPlayerForge(IEventBus modEventBus, ModContainer modContainer) {
    IamMusicPlayer.init();
    modEventBus.addListener(this::setup);
  }

  private void setup(FMLCommonSetupEvent e) {
    IamMusicPlayer.setup();
  }
}
