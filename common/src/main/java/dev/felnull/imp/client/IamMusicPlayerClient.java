package dev.felnull.imp.client;

import dev.architectury.platform.Platform;
import dev.felnull.imp.IMPConfig;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.gui.config.Button;
import dev.felnull.imp.client.gui.config.ButtonGuiProvider;
import dev.felnull.imp.client.gui.screen.IMPScreenFactorys;
import dev.felnull.imp.client.gui.screen.monitor.boombox.BoomboxMonitor;
import dev.felnull.imp.client.gui.screen.monitor.cassette_deck.CassetteDeckMonitor;
import dev.felnull.imp.client.gui.screen.monitor.music_manager.MusicManagerMonitor;
import dev.felnull.imp.client.handler.ClientHandler;
import dev.felnull.imp.client.handler.RenderHandler;
import dev.felnull.imp.client.handler.TextureHandler;
import dev.felnull.imp.client.lava.LavaPlayerLoader;
import dev.felnull.imp.client.lava.LavaPlayerManager;
import dev.felnull.imp.client.music.IMPMusicTrackerFactory;
import dev.felnull.imp.client.music.loader.IMPMusicLoaders;
import dev.felnull.imp.client.music.lyric.IMPLyricGetter;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.client.renderer.blockentity.IMPBlockEntityRenderers;
import dev.felnull.imp.client.renderer.item.IMPItemRenderers;
import dev.felnull.imp.networking.IMPPackets;
import dev.felnull.imp.util.GiteeURL;
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
        IMPLyricGetter.init();
        IMPMusicTrackerFactory.init();
        LavaPlayerManager.getInstance().reload();

        ClientHandler.init();
    }

    private static void configInit() {
        Platform.getMod(IamMusicPlayer.MODID).registerConfigurationScreen(parent -> {
            /*ConfigScreenProvider<IMPConfig> provider = (ConfigScreenProvider<IMPConfig>) AutoConfig.getConfigScreen(IMPConfig.class, parent);
            provider.setBuildFunction(builder -> {
                builder.setGlobalized(true);
                builder.setGlobalizedExpanded(false);
                return builder.build();
            });*/
            GiteeURL.trySet();
            return AutoConfig.getConfigScreen(IMPConfig.class, parent).get();
        });
        AutoConfig.getGuiRegistry(IMPConfig.class).registerAnnotationProvider(new ButtonGuiProvider(), Button.class);
    }
}
