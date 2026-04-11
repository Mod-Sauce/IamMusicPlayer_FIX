package dev.felnull.imp.integration;

import dev.felnull.imp.IamMusicPlayer;
import org.modsauce.otyacraftenginerenewed.integration.BaseIntegration;

public class CreateIntegration extends BaseIntegration {
    public static final CreateIntegration INSTANCE = new CreateIntegration();

    @Override
    public String getModId() {
        return "create";
    }

    @Override
    public boolean isConfigEnabled() {
        return IamMusicPlayer.getConfig().createIntegration;
    }
}
