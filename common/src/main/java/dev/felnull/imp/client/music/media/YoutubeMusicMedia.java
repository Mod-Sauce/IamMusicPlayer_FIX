package dev.felnull.imp.client.music.media;

import com.google.common.collect.ImmutableList;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.felnull.imp.client.lava.LavaPlayerManager;
import dev.felnull.imp.client.lava.YoutubeRemoteConfig;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.AndroidMusic;
import dev.lavalink.youtube.clients.AndroidVr;
import dev.lavalink.youtube.clients.MWeb;
import dev.lavalink.youtube.clients.Music;
import dev.lavalink.youtube.clients.Web;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class YoutubeMusicMedia extends LavaPlayerBaseMusicMedia {
    private static final Component YT_ENTER_TEXT = new TranslatableComponent("imp.text.enterText.youtube");

    protected YoutubeMusicMedia(String name) {
        super(name);
    }

    @Override
    public void registerSourceManager(AudioPlayerManager audioPlayerManager) {
        YoutubeRemoteConfig config = YoutubeRemoteConfig.load();
        config.applyClientPoToken();

        YoutubeSourceOptions options = new YoutubeSourceOptions()
                .setAllowSearch(true)
                .setRemoteCipher(config.cipherUrl, config.cipherPassword, config.getUserAgent());

        YoutubeAudioSourceManager source = new YoutubeAudioSourceManager(
                options,
                new AndroidVr(),
                new AndroidMusic(),
                new Music(),
                new Web(),
                new MWeb()
        );
        audioPlayerManager.registerSourceManager(source);
    }

    @Override
    public boolean match(AudioTrack track) {
        return track.getSourceManager() instanceof YoutubeAudioSourceManager;
    }

    @Override
    public boolean isSearchable() {
        return true;
    }

    @Override
    protected ImageInfo createThumbnail(AudioTrack track) {
        return new ImageInfo(ImageInfo.ImageType.YOUTUBE_THUMBNAIL, track.getIdentifier());
    }

    @Override
    public List<MusicMediaResult> search(String searchText) {
        if (searchText.isEmpty())
            return new ArrayList<>();

        var lm = LavaPlayerManager.getInstance();
        List<AudioTrack> tracks;

        try {
            tracks = lm.searchYoutube(searchText);
        } catch (ExecutionException | InterruptedException e) {
            return ImmutableList.of();
        }

        return tracks.stream().map(this::createResult).toList();
    }

    @Override
    public Component getEnterText() {
        return YT_ENTER_TEXT;
    }
}
