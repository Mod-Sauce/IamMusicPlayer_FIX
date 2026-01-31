package dev.felnull.imp.client.integration;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.explatform.IMPMaidExpectPlatform;
import org.modsauce.otyacraftenginerenewed.integration.BaseIntegration;

public class TouhouLittleMaidIntegration extends BaseIntegration {
    public static final TouhouLittleMaidIntegration INSTANCE = new TouhouLittleMaidIntegration();
    @Override
    public String getModId() {
        return "touhou_little_maid";
    }

    @Override
    public boolean isConfigEnabled() {
        return IamMusicPlayer.getConfig().touhouLittleMaidIntegration;
    }

    public void init(){
        IMPMaidExpectPlatform.init();
    }
}
