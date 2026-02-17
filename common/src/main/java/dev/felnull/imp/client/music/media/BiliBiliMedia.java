package dev.felnull.imp.client.music.media;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.bilibili.BiliBiliUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class BiliBiliMedia extends LavaPlayerBaseMusicMedia {
    protected BiliBiliMedia(String name) {
        super(name);
    }

    @Override
    public void registerSourceManager(AudioPlayerManager audioPlayerManager) {
        audioPlayerManager.registerSourceManager(new HttpAudioSourceManager());
    }

    @Override
    public Component getMediaName() {
        return Component.literal("bilibili");
    }

    @Override
    public Component getEnterText() {
        return Component.literal("bv");
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
        return BiliBiliUtil.getMusicMediaResultFromBV(sourceName);
    }

    @Override
    public boolean match(AudioTrack track) {
        return track.getSourceManager() instanceof HttpAudioSourceManager;
    }

    @Override
    public int priority() {
        return 1;
    }
}
