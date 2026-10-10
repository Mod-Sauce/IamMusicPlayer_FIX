package dev.felnull.imp.client.music.media;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.music.resource.ImageInfo;
import net.minecraft.network.chat.Component;

public class SoundCloudMusicMedia extends LavaPlayerBaseMusicMedia {
    private static final Component SC_ENTER_TEXT = Component.translatable("imp.text.enterText.soundcloud");

    protected SoundCloudMusicMedia(String name) {
        super(name);
    }

    @Override
    public MusicMediaResult load(String sourceName) throws Exception {
        // Cannot be loaded once disabled (also absent from the list, see IMPMusicMedias#showInLoaderList)
        if (!IamMusicPlayer.getConfig().soundCloudConfig.enableSoundCloud)
            return null;
        return super.load(sourceName);
    }

    @Override
    public void registerSourceManager(AudioPlayerManager audioPlayerManager) {
        audioPlayerManager.registerSourceManager(SoundCloudAudioSourceManager.createDefault());
    }

    @Override
    public boolean match(AudioTrack track) {
        return track.getSourceManager() instanceof SoundCloudAudioSourceManager;
    }

    @Override
    protected String getIdentifier(AudioTrack track) {
        return track.getInfo().uri;
    }

    @Override
    protected ImageInfo createThumbnail(AudioTrack track) {
        return new ImageInfo(ImageInfo.ImageType.SOUND_CLOUD_ARTWORK, track.getInfo().uri);
    }

    @Override
    public boolean isSearchable() {
        return false;
    }

    @Override
    public Component getEnterText() {
        return SC_ENTER_TEXT;
    }
}
