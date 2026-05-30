package dev.felnull.imp.forge;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.create.IMPCreate;
import dev.felnull.imp.integration.CreateIntegration;
import dev.felnull.imp.integration.NetMusicListIntegration;
import dev.felnull.imp.netmusiclist.IMPNetMusicList;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(IamMusicPlayer.MODID)
public class IamMusicPlayerForge {

  public IamMusicPlayerForge(IEventBus eventBus) {
    IMPCriteriaTriggersNeoForge.registry(eventBus);
    IamMusicPlayer.init();

    if(CreateIntegration.INSTANCE.isEnable())
      IMPCreate.init(eventBus);
    if(NetMusicListIntegration.INSTANCE.isEnable())
      IMPNetMusicList.init();

    eventBus.addListener(this::setup);
  }

  private void setup(FMLCommonSetupEvent e) {
    IamMusicPlayer.setup();
  }
}
