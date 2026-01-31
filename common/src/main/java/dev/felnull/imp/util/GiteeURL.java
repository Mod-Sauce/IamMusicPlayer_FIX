package dev.felnull.imp.util;

import dev.felnull.imp.IMPConfig;
import dev.felnull.imp.IamMusicPlayer;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.Minecraft;

public class GiteeURL {
    public static final String hashBaseUrl = "https://gitee.com/gly091020/test_lavaplayer_IMP/raw/main/lavaplayer";
    public static final String lavaPlayerNativesURL = "https://gitee.com/gly091020/test_lavaplayer_IMP/raw/main/lavaplayer/natives_link.json";

    public static void setGitee(){
        IamMusicPlayer.getConfig().hashBaseUrl = hashBaseUrl;
        IamMusicPlayer.getConfig().lavaPlayerNativesURL = lavaPlayerNativesURL;
        reload();
    }

    public static void reload(){
        var h = AutoConfig.getConfigHolder(IMPConfig.class);
        h.setConfig(IamMusicPlayer.getConfig());
        h.save();
    }

    public static boolean isChina(){
        var manager = Minecraft.getInstance().getLanguageManager();
        return manager.getSelected().equals("zh_cn");
    }

    public static void trySet(){
        if(!IamMusicPlayer.getConfig().tryUseGitee)return;
        if(isChina() && IamMusicPlayer.getConfig().lavaPlayerNativesURL.contains("raw.githubusercontent.com"))
            setGitee();
        else if(!isChina()){
            IamMusicPlayer.getConfig().tryUseGitee = false;
            reload();
        }
    }
}
