package org.modsauce.impr.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.modsauce.impr.IamMusicPlayer;

@Mod(IamMusicPlayer.MODID)
public class IamMusicPlayerNeoForge {

  public IamMusicPlayerNeoForge(
    IEventBus modEventBus,
    ModContainer modContainer
  ) {
    IamMusicPlayer.init();
    modEventBus.addListener(this::setup);
  }

  private void setup(FMLCommonSetupEvent e) {
    IamMusicPlayer.setup();
  }
}
