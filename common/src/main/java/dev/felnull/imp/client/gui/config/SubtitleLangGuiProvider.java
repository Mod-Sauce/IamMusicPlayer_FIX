package dev.felnull.imp.client.gui.config;

import dev.felnull.imp.IMPConfig;

/**
 * The Bilibili subtitle-language preference ({@link IMPConfig.SubtitleLang}) in the config screen: a click-to-cycle row labelled from {@code imp.subtitleLang.*} instead of Cloth's raw constant names.
 */
public final class SubtitleLangGuiProvider extends EnumCycleGuiProvider<IMPConfig.SubtitleLang> {
    public SubtitleLangGuiProvider() {
        super(IMPConfig.SubtitleLang.class,
                "text.autoconfig.iammusicplayer.option.bilibiliConfig.subtitleLang",
                "imp.subtitleLang.");
    }
}
