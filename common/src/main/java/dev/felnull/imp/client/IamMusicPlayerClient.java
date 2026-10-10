package dev.felnull.imp.client;

import dev.architectury.platform.Platform;
import dev.felnull.imp.IMPConfig;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.gui.config.button.Button;
import dev.felnull.imp.client.gui.config.button.ButtonGuiProvider;
import dev.felnull.imp.client.gui.config.proxy.ProxyGuiProvider;
import dev.felnull.imp.client.gui.config.proxy.UserProxy;
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
import dev.felnull.imp.client.music.playlist.IMPPlaylistLoaders;
import dev.felnull.imp.client.renderer.blockentity.IMPBlockEntityRenderers;
import dev.felnull.imp.client.renderer.item.IMPItemRenderers;
import dev.felnull.imp.networking.IMPPackets;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class IamMusicPlayerClient {
    static {
        // Boombox sort name in the config screen: AutoConfig ignores the language file for enums, so we need a name provider
        me.shedaniel.autoconfig.AutoConfig.getGuiRegistry(dev.felnull.imp.IMPConfig.class)
                .registerTypeProvider(new dev.felnull.imp.client.gui.config.BoomboxSongSortGuiProvider(),
                        dev.felnull.imp.IMPConfig.BoomboxSongSort.class);
        // Same for the handheld bottom-HUD style (PLAIN / STRIP / BOX)
        me.shedaniel.autoconfig.AutoConfig.getGuiRegistry(dev.felnull.imp.IMPConfig.class)
                .registerTypeProvider(new dev.felnull.imp.client.gui.config.HudBottomStyleGuiProvider(),
                        dev.felnull.imp.IMPConfig.HudBottomStyle.class);
        // Same for the B站 subtitle-translation choice (SMART / EN / JA / FIRST)
        me.shedaniel.autoconfig.AutoConfig.getGuiRegistry(dev.felnull.imp.IMPConfig.class)
                .registerTypeProvider(new dev.felnull.imp.client.gui.config.SubtitleTransGuiProvider(),
                        dev.felnull.imp.IMPConfig.SubtitleTrans.class);
        // Same for the B站 subtitle-track choice (ALL / HUMAN_ONLY / AI_ONLY)
        me.shedaniel.autoconfig.AutoConfig.getGuiRegistry(dev.felnull.imp.IMPConfig.class)
                .registerTypeProvider(new dev.felnull.imp.client.gui.config.SubtitleTrackGuiProvider(),
                        dev.felnull.imp.IMPConfig.SubtitleTrack.class);
        // Same for the preferred subtitle language (BUILTIN / ZH / EN / JA / KO)
        me.shedaniel.autoconfig.AutoConfig.getGuiRegistry(dev.felnull.imp.IMPConfig.class)
                .registerTypeProvider(new dev.felnull.imp.client.gui.config.SubtitleLangGuiProvider(),
                        dev.felnull.imp.IMPConfig.SubtitleLang.class);
        // Same for the 「刷新」 import scope (AUTO / SEASON / ZONE / ALL)
        me.shedaniel.autoconfig.AutoConfig.getGuiRegistry(dev.felnull.imp.IMPConfig.class)
                .registerTypeProvider(new dev.felnull.imp.client.gui.config.RefreshScopeGuiProvider(),
                        dev.felnull.imp.IMPConfig.RefreshScope.class);
    }

    public static final OptionInstance<Double> IMP_VOLUME_OPTION = new OptionInstance<>("soundCategory." + IamMusicPlayer.MODID, OptionInstance.noTooltip(), (component, val) -> {
        return val == 0.0 ? Options.genericValueLabel(component, CommonComponents.OPTION_OFF) : Component.translatable("options.percent_value", component, (int) (val * 100.0));
    }, OptionInstance.UnitDouble.INSTANCE, IamMusicPlayer.getConfig().volume, (val) -> IamMusicPlayer.getConfig().volume = val);

    public static void init() {
        configInit();
        // 旧键迁移要在任何一次配置保存之前跑：Cloth 保存时会用新字段名重写整个文件
        dev.felnull.imp.client.music.lyric.BilibiliLyricGetter.migrateLegacySwitch();
        normalizeBilibiliCookie();        LavaPlayerLoader.init();
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
        IMPPlaylistLoaders.init();
        IMPMusicTrackerFactory.init();
        LavaPlayerManager.getInstance().reload();
        // Key bindings (currently 「跳分P」): must be registered during mod init, before vanilla builds its options
        IMPKeyMappings.init();

        ClientHandler.init();
    }

    /** Housekeeping for the B站 Cookie field: a phone client (Piliplus) exports its login as JSON and users paste that
     * blob; {@code BiliBiliCookie} parses either form, but storing the compact {@code name=value; …} form means the
     * config does not also carry the export's {@code accessKey} / {@code refresh} credentials. */
    private static void normalizeBilibiliCookie() {
        try {
            var cfg = IamMusicPlayer.getConfig().bilibiliConfig;
            if (cfg == null || cfg.bilibiliCookie == null || !cfg.bilibiliCookie.contains("{"))
                return;
            var parsed = dev.felnull.imp.client.bilibili.BiliBiliCookie.parse(cfg.bilibiliCookie);
            if (parsed.isEmpty() || parsed.equals(cfg.bilibiliCookie))
                return;
            cfg.bilibiliCookie = parsed;
            AutoConfig.getConfigHolder(IMPConfig.class).save();
        } catch (Throwable ignored) {
            // Cosmetic only: the request path parses the value anyway
        }
    }

    private static void configInit() {        Platform.getMod(IamMusicPlayer.MODID).registerConfigurationScreen(parent -> {
            /*ConfigScreenProvider<IMPConfig> provider = (ConfigScreenProvider<IMPConfig>) AutoConfig.getConfigScreen(IMPConfig.class, parent);
            provider.setBuildFunction(builder -> {
                builder.setGlobalized(true);
                builder.setGlobalizedExpanded(false);
                return builder.build();
            });*/
            return AutoConfig.getConfigScreen(IMPConfig.class, parent).get();
        });
        AutoConfig.getGuiRegistry(IMPConfig.class).registerAnnotationProvider(new ButtonGuiProvider(), Button.class);
        AutoConfig.getGuiRegistry(IMPConfig.class).registerTypeProvider(new ProxyGuiProvider(), UserProxy.class);
    }
}
