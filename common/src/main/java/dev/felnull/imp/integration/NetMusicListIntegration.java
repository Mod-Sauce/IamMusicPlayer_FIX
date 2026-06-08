package dev.felnull.imp.integration;

import dev.felnull.imp.IamMusicPlayer;
import org.modsauce.otyacraftenginerenewed.integration.BaseIntegration;

public class NetMusicListIntegration extends BaseIntegration {
    public static final NetMusicListIntegration INSTANCE = new NetMusicListIntegration();
    @Override
    public String getModId() {
        return "net_music_list";
    }

    @Override
    public boolean isConfigEnabled() {
        return IamMusicPlayer.getConfig().netMusicListIntegration;
    }
}
