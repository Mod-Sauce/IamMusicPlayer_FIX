package dev.felnull.imp.client.music.loader;

import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.felnull.imp.client.cache.AudioCacheManager;
import dev.felnull.imp.client.lava.LavaPlayerManager;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.client.music.player.LavaMusicPlayer;
import dev.felnull.imp.client.music.player.MusicPlayer;
import dev.felnull.imp.music.resource.MusicSource;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public class CacheMusicLoader implements MusicLoader{
    private MusicSource musicSource;
    private AudioTrack audioTrack;

    private static final LocalAudioSourceManager MANAGER = new LocalAudioSourceManager();
    @Override
    public @NotNull MusicPlayer<?, ?> createMusicPlayer(UUID musicPlayerId) {
        return new LavaMusicPlayer(musicPlayerId, audioTrack, musicSource);
    }

    @Override
    public void tryLoad(@NotNull MusicSource source) {
        var id = source.getIdentifier();
        if(Objects.equals(source.getLoaderType(), IMPMusicMedias.NETEASE_MUSIC.getName()))
            id = "netease:" + id;
        else if(Objects.equals(source.getLoaderType(), IMPMusicMedias.BILIBILI.getName()))
            id = "bilibili:" + id;
        if(!AudioCacheManager.has(id))
            throw new RuntimeException();
        var file = AudioCacheManager.getPath(id);
        if(file == null)
            throw new RuntimeException();

        var result = MANAGER.loadItem(LavaPlayerManager.getInstance().getAudioPlayerManager(), new AudioReference(file.toString(),
                ""));
        if(!(result instanceof AudioTrack track))
            throw new RuntimeException();
        audioTrack = track;
        musicSource = source;
    }

    @Override
    public int priority() {
        return 114514;
    }
}
