package dev.felnull.imp.forge;

import dev.felnull.imp.IamMusicPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(IamMusicPlayer.MODID)
public class IamMusicPlayerForge {

  public IamMusicPlayerForge(IEventBus eventBus) {
      IMPCriteriaTriggersNeoForge.registry(eventBus);
    IamMusicPlayer.init();
    eventBus.addListener(this::setup);
  }

  private void setup(FMLCommonSetupEvent e) {
    IamMusicPlayer.setup();
  }
}
