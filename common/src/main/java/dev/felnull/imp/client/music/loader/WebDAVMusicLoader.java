package dev.felnull.imp.client.music.loader;

import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.lava.LavaPlayerManager;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.client.music.player.LavaMusicPlayer;
import dev.felnull.imp.client.music.player.MusicPlayer;
import dev.felnull.imp.client.webdav.WebDAVUtil;
import dev.felnull.imp.music.resource.MusicSource;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.UUID;

public class WebDAVMusicLoader implements MusicLoader {
    private static final LocalAudioSourceManager MANAGER = new LocalAudioSourceManager();
    private MusicSource musicSource;
    private AudioTrack audioTrack;

    @Override
    public @NotNull MusicPlayer<?, ?> createMusicPlayer(UUID musicPlayerId) {
        return new LavaMusicPlayer(musicPlayerId, audioTrack, musicSource);
    }

    @Override
    public void tryLoad(@NotNull MusicSource source) throws Exception {
        if (!IamMusicPlayer.getConfig().webDAVConfig.enableWebDAV) return;
        if (!IMPMusicMedias.WEBDAV.getName().equals(source.getLoaderType())) throw new RuntimeException("Unsupported media");
        Path file = WebDAVUtil.downloadToTempFile(source.getIdentifier());
        if (file == null) throw new RuntimeException("Failed to download WebDAV file");
        var result = MANAGER.loadItem(LavaPlayerManager.getInstance().getAudioPlayerManager(), new AudioReference(file.toString(), ""));
        if (!(result instanceof AudioTrack track)) throw new RuntimeException("Failed to load WebDAV file");
        this.audioTrack = track;
        this.musicSource = source;
    }

    @Override
    public int priority() {
        return 2;
    }
}
