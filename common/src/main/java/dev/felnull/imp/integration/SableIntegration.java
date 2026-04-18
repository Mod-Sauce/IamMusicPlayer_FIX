package dev.felnull.imp.integration;

import dev.felnull.imp.IamMusicPlayer;
import org.modsauce.otyacraftenginerenewed.integration.BaseIntegration;

public class SableIntegration extends BaseIntegration {
    public static final SableIntegration INSTANCE = new SableIntegration();

    @Override
    public String getModId() {
        return "sable";
    }

    @Override
    public boolean isConfigEnabled() {
        return IamMusicPlayer.getConfig().sableIntegration;
    }
}
