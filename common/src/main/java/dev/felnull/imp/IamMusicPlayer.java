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
import dev.felnull.imp.inventory.IMPMenus;
import dev.felnull.imp.item.IMPCreativeModeTabs;
import dev.felnull.imp.item.IMPItems;
import dev.felnull.imp.networking.IMPPackets;
import dev.felnull.imp.server.handler.ServerHandler;
import dev.felnull.imp.server.handler.ServerMusicHandler;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IamMusicPlayer {

    public static final String MODID = "iammusicplayer";
    public static final String LAVAPLAYERNATIVESVERSION = "2.2.6";
    public static final String CONFIG_VERSION = "3";
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Supplier<String> MODNAME = Suppliers.memoize(() ->
        Platform.getMod(MODID).getName()
    );
    private static final IMPConfig CONFIG = AutoConfig.register(
        IMPConfig.class,
        Toml4jConfigSerializer::new
    ).getConfig();

    public static void init() {
        checkAndResetConfig();
        IMPPackets.init();
        IMPCreativeModeTabs.init();
        IMPItems.init();
        IMPBlocks.init();
        IMPBlockEntities.init();
        IMPMenus.init();
        IMPPoiType.init();
        IMPVillagerProfessions.init();
        IMPCriteriaTriggers.init();
        ServerMusicHandler.init();
        ServerHandler.init();
        CommonHandler.init();
    }

    public static void setup() {
        IMPVillagerProfessions.setup();
    }

    public static String getModName() {
        return MODNAME.get();
    }

    public static IMPConfig getConfig() {
        return CONFIG;
    }

    private static void checkAndResetConfig() {
        boolean needsReset =
            CONFIG.configVersion == null ||
            CONFIG.configVersion.isEmpty() ||
            !CONFIG.configVersion.equals(CONFIG_VERSION);

        if (needsReset) {
            LOGGER.info(
                "First launch of IamMusicPlayer Renewed fork detected or config version mismatch. Current version: {}, Expected version: {}. Resetting config to defaults...",
                CONFIG.configVersion,
                CONFIG_VERSION
            );

            // First reset attempt
            performConfigReset();

            // Second reset attempt to ensure persistence (especially on Linux)
            LOGGER.info(
                "Performing second config reset to ensure all values are properly persisted..."
            );
            performConfigReset();

            LOGGER.info(
                "Config reset completed. Two reset attempts were made to ensure reliability."
            );
        } else {
            LOGGER.debug(
                "Config version matches. Current version: {}",
                CONFIG.configVersion
            );
        }
    }

    private static void performConfigReset() {
        // Reset all config values to defaults
        CONFIG.volume = 1f;
        CONFIG.maxPlayCont = 8;
        CONFIG.spatial = true;
        CONFIG.sampleRate = 44100;
        CONFIG.useYoutubeDownloader = true;
        CONFIG.relayServerURL =
            "https://raw.githubusercontent.com/TeamFelnull/IamMusicPlayer/master/relay_server.json";
        CONFIG.lavaPlayerNativesURL =
            "https://raw.githubusercontent.com/Mod-Sauce/lavanatives/refs/heads/main/lavaplayer/natives_link.json";
        CONFIG.hashBaseUrl =
            "https://raw.githubusercontent.com/Mod-Sauce/lavanatives/refs/heads/main/lavaplayer";
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
        CONFIG.ConnectionTimeout = 10000; // 10 seconds
        CONFIG.ReadTimeout = 30000; // 30 seconds
        CONFIG.DownloadRetryCount = 3;
        CONFIG.DownloadRetryDelayMS = 1000;

        // Update config version FIRST before saving
        CONFIG.configVersion = CONFIG_VERSION;

        // Save the config with error handling
        try {
            AutoConfig.getConfigHolder(IMPConfig.class).save();
            LOGGER.info(
                "Config has been reset to defaults and saved successfully."
            );
        } catch (Exception e) {
            LOGGER.error(
                "Failed to save config after reset. This may cause the config to reset on every launch.",
                e
            );
        }
    }
}
