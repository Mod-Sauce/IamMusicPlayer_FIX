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
import dev.felnull.imp.item.IMPItems;
import dev.felnull.imp.networking.IMPPackets;
import dev.felnull.imp.server.handler.ServerHandler;
import dev.felnull.imp.server.handler.ServerMusicHandler;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;

public class IamMusicPlayer {

    public static final IMPConfig CONFIG = AutoConfig.register(
        IMPConfig.class,
        Toml4jConfigSerializer::new
    ).getConfig();
    public static final String MODID = "iammusicplayer";
    public static final String CONFIG_VERSION = "1";
    private static final Supplier<String> MODNAME = Suppliers.memoize(() ->
        Platform.getMod(MODID).getName()
    );

    public static void init() {
        IMPPackets.init();
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

    private static void checkAndResetConfig() {
        if (
            CONFIG.configVersion == null ||
            CONFIG.configVersion.isEmpty() ||
            !CONFIG.configVersion.equals(CONFIG_VERSION)
        ) {
            // Reset all config values to defaults
            CONFIG.volume = 1f;
            CONFIG.maxPlayCont = 8;
            CONFIG.spatial = true;
            CONFIG.sampleRate = 44100;
            CONFIG.useYoutubeDownloader = true;
            CONFIG.relayServerURL =
                "https://raw.githubusercontent.com/TeamFelnull/IamMusicPlayer/master/relay_server.json";
            CONFIG.lavaPlayerNativesURL =
                "https://raw.githubusercontent.com/Mod-Sauce/test_lavaplayer_IMP/refs/heads/main/lavaplayer/natives_link.json";
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
        }
    }

    public static void setup() {
        IMPVillagerProfessions.setup();
    }

    public static String getModName() {
        return MODNAME.get();
    }
}
