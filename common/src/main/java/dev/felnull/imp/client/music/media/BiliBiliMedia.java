package dev.felnull.imp.client.music.media;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.bilibili.BiliBiliUtil;
import dev.felnull.imp.client.music.sourceManager.BilibiliHttpAudioSourceManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.regex.Pattern;

public class BiliBiliMedia extends LavaPlayerBaseMusicMedia {
    private static final Pattern PATTERN = Pattern.compile("BV[a-zA-Z0-9]{10}");
    protected BiliBiliMedia(String name) {
        super(name);
    }

    @Override
    public void registerSourceManager(AudioPlayerManager audioPlayerManager) {
        audioPlayerManager.registerSourceManager(new BilibiliHttpAudioSourceManager());
    }

    @Override
    public Component getMediaName() {
        return Component.translatable("imp.loaderType.bilibili");
    }

    @Override
    public Component getEnterText() {
        return Component.translatable("imp.text.enterText.bilibili");
    }

    @Override
    public ResourceLocation getIcon() {
        return ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID,
                "textures/gui/container/music_manager/loader_types/bilibili.png");
    }

    @Override
    public boolean isSearchable() {
        return false;
    }

    @Override
    public MusicMediaResult load(String sourceName) throws Exception {
        if(!IamMusicPlayer.getConfig().bilibiliConfig.enableBilibili)return null;
        var m = PATTERN.matcher(sourceName);
        if(m.find())
            sourceName = m.group();
        return BiliBiliUtil.getMusicMediaResultFromBV(sourceName);
    }

    @Override
    public boolean match(AudioTrack track) {
        return track.getSourceManager() instanceof BilibiliHttpAudioSourceManager;
    }

    @Override
    public int priority() {
        return 2;
    }
}
