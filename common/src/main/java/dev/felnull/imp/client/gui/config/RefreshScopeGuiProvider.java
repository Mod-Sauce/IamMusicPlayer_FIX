package dev.felnull.imp.client.gui.config;

import dev.felnull.imp.IMPConfig;

/** The 「刷新」 import scope ({@link IMPConfig.RefreshScope}) in the config screen: a click-to-cycle row whose values are
 * labelled from {@code imp.refreshScope.*} instead of Cloth's raw constant names. */
public final class RefreshScopeGuiProvider extends EnumCycleGuiProvider<IMPConfig.RefreshScope> {
    public RefreshScopeGuiProvider() {
        super(IMPConfig.RefreshScope.class,
                "text.autoconfig.iammusicplayer.option.bilibiliConfig.refreshScope",
                "imp.refreshScope.");
    }
}
