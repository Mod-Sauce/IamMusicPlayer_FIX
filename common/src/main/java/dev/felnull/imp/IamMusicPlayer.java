package dev.felnull.imp;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import dev.architectury.platform.Platform;
import dev.felnull.imp.advancements.IMPCriteriaTriggers;
import dev.felnull.imp.block.IMPBlocks;
import dev.felnull.imp.blockentity.IMPBlockEntities;
import dev.felnull.imp.entity.village.IMPPoiType;
import dev.felnull.imp.entity.village.IMPVillagerProfessions;
import dev.felnull.imp.handler.CommonHandler;
import dev.felnull.imp.integration.CCTIntegration;
import dev.felnull.imp.inventory.IMPMenus;
import dev.felnull.imp.item.IMPCreativeModeTabs;
import dev.felnull.imp.item.IMPItems;
import dev.felnull.imp.item.component.IMPComponents;
import dev.felnull.imp.networking.IMPPackets;
import dev.felnull.imp.server.handler.ServerHandler;
import dev.felnull.imp.server.handler.ServerMusicHandler;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class IamMusicPlayer {

  public static final String MODID = "iammusicplayer";
  public static final String CONFIG_VERSION = "1";
  private static final Logger LOGGER = LogManager.getLogger();
  private static final Supplier<String> MODNAME = Suppliers.memoize(
    () -> Platform.getMod(MODID).getName()
  );
  private static final IMPConfig CONFIG = AutoConfig.register(
    IMPConfig.class,
    Toml4jConfigSerializer::new
  ).getConfig();

  public static void init() {
    checkAndResetConfig();
    IMPCreativeModeTabs.init();
    IMPComponents.init();
    IMPItems.init();
    IMPBlocks.init();
    IMPBlockEntities.init();
    IMPMenus.init();
    IMPPoiType.init();
    IMPVillagerProfessions.init();
    ServerMusicHandler.init();
    ServerHandler.init();
    CommonHandler.init();
    IMPPackets.init();
    CCTIntegration.INSTANCE.init();
  }

  private static void checkAndResetConfig() {
    if (
      CONFIG.configVersion == null ||
      CONFIG.configVersion.isEmpty() ||
      !CONFIG.configVersion.equals(CONFIG_VERSION)
    ) {
      LOGGER.info(
        "First launch of IamMusicPlayer Renewed fork detected or config version mismatch. Resetting config to defaults..."
      );

      // Reset all config values to defaults
      CONFIG.volume = 1f;
      CONFIG.maxPlayCont = 8;
      CONFIG.spatial = true;
      CONFIG.sampleRate = 44100;
      CONFIG.useYoutubeDownloader = true;
      CONFIG.relayServerURL =
        "https://raw.githubusercontent.com/TeamFelnull/IamMusicPlayer/master/relay_server.json";
      CONFIG.IMPRFolder = "iammusicplayerrenewed";
      CONFIG.lavaNativesFolder = "lavaplayer_natives";
      CONFIG.hideDisplaySprite = false;
      CONFIG.hideDecorativeAntenna = false;
      CONFIG.maxWaitTime = 1000 * 10;
      CONFIG.retryTime = 1000 * 3;
      CONFIG.dropItemRing = true;
      CONFIG.soundPhysicsRemasteredIntegration = true;
      CONFIG.showMusicLines = false;
      CONFIG.showSpeakerRange = false;

      // Update config version
      CONFIG.configVersion = CONFIG_VERSION;

      // Save the config
      AutoConfig.getConfigHolder(IMPConfig.class).save();

      LOGGER.info(
        "Config has been reset to defaults for IamMusicPlayer Renewed."
      );
    }
  }

  public static void setup() {
    IMPVillagerProfessions.setup();
    if(!Platform.isNeoForge())
      IMPCriteriaTriggers.init();
    // There is special code for NeoForge, no changes are needed.
  }

  public static String getModName() {
    return MODNAME.get();
  }

  public static IMPConfig getConfig() {
    return CONFIG;
  }
}
