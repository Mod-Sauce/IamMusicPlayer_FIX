package dev.felnull.imp.integration;

import dev.felnull.imp.IamMusicPlayer;
import org.modsauce.otyacraftenginerenewed.integration.BaseIntegration;

public class JadeIntegration extends BaseIntegration {
    public static final JadeIntegration INSTANCE = new JadeIntegration();

    @Override
    public String getModId() {
        return "jade";
    }

    @Override
    public boolean isConfigEnabled() {
        return IamMusicPlayer.getConfig().jadeIntegration;
    }
}
