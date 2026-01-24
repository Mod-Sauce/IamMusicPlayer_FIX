package dev.felnull.imp.forge;

import dev.felnull.imp.IamMusicPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@SuppressWarnings("Deprecated")
@Mod(IamMusicPlayer.MODID)
public class IamMusicPlayerForge {

  public IamMusicPlayerForge(IEventBus eventBus) {
//    EventBuses.registerModEventBus(
//      IamMusicPlayer.MODID,
//      eventBus
//    ); // todo:whyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy
    IamMusicPlayer.init();
    eventBus.addListener(this::setup);
  }

  private void setup(FMLCommonSetupEvent e) {
    IamMusicPlayer.setup();
  }
}
