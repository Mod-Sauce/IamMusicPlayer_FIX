package org.modsauce.impr.client;

import dev.architectury.platform.Platform;
import org.modsauce.impr.IMPConfig;
import org.modsauce.impr.IamMusicPlayer;
import org.modsauce.impr.client.gui.screen.IMPScreenFactorys;
import org.modsauce.impr.client.gui.screen.monitor.boombox.BoomboxMonitor;
import org.modsauce.impr.client.gui.screen.monitor.cassette_deck.CassetteDeckMonitor;
import org.modsauce.impr.client.gui.screen.monitor.music_manager.MusicManagerMonitor;
import org.modsauce.impr.client.handler.ClientHandler;
import org.modsauce.impr.client.handler.RenderHandler;
import org.modsauce.impr.client.handler.TextureHandler;
import org.modsauce.impr.client.lava.LavaPlayerLoader;
import org.modsauce.impr.client.lava.LavaPlayerManager;
import org.modsauce.impr.client.music.IMPMusicTrackerFactory;
import org.modsauce.impr.client.music.loader.IMPMusicLoaders;
import org.modsauce.impr.client.music.media.IMPMusicMedias;
import org.modsauce.impr.client.renderer.blockentity.IMPBlockEntityRenderers;
import org.modsauce.impr.client.renderer.item.IMPItemRenderers;
import org.modsauce.impr.networking.IMPPackets;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class IamMusicPlayerClient {
    public static final OptionInstance<Double> IMP_VOLUME_OPTION = new OptionInstance<>("soundCategory." + IamMusicPlayer.MODID, OptionInstance.noTooltip(), (component, val) -> {
        return val == 0.0 ? Options.genericValueLabel(component, CommonComponents.OPTION_OFF) : Component.translatable("options.percent_value", component, (int) (val * 100.0));
    }, OptionInstance.UnitDouble.INSTANCE, IamMusicPlayer.getConfig().volume, (val) -> IamMusicPlayer.getConfig().volume = val);

    public static void init() {
        configInit();
        LavaPlayerLoader.init();
        IMPPackets.clientInit();
        ClientHandler.init();
        RenderHandler.init();
        TextureHandler.init();
        IMPBlockEntityRenderers.init();
        IMPItemRenderers.init();
        IMPScreenFactorys.init();
        MusicManagerMonitor.firstInit();
        CassetteDeckMonitor.firstInit();
        BoomboxMonitor.firstInit();

        IMPMusicMedias.init();
        IMPMusicLoaders.init();
        IMPMusicTrackerFactory.init();
        LavaPlayerManager.getInstance().reload();
    }

    private static void configInit() {
        Platform.getMod(IamMusicPlayer.MODID).registerConfigurationScreen(parent -> {
            /*ConfigScreenProvider<IMPConfig> provider = (ConfigScreenProvider<IMPConfig>) AutoConfig.getConfigScreen(IMPConfig.class, parent);
            provider.setBuildFunction(builder -> {
                builder.setGlobalized(true);
                builder.setGlobalizedExpanded(false);
                return builder.build();
            });*/
            return AutoConfig.getConfigScreen(IMPConfig.class, parent).get();
        });
    }
}
