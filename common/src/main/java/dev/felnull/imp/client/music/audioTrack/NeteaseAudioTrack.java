package dev.felnull.imp.client.music.audioTrack;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import dev.felnull.imp.client.music.sourceManager.NeteaseSourceManager;

import java.net.URI;

public class NeteaseAudioTrack extends DelegatedAudioTrack {
    private final NeteaseSourceManager sourceManager;
    public NeteaseAudioTrack(AudioTrackInfo trackInfo, NeteaseSourceManager sourceManager) {
        super(trackInfo);
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface();
             PersistentHttpStream stream = new PersistentHttpStream(
                     httpInterface,
                     new URI(trackInfo.uri),
                     -1L
             )) {

            InternalAudioTrack delegate = new Mp3AudioTrack(trackInfo, stream);

            processDelegate(delegate, executor);
        }
    }

    @Override
    public NeteaseSourceManager getSourceManager() {
        return sourceManager;
    }
}
