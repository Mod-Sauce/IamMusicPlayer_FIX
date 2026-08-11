package dev.felnull.imp.client.music.audioTrack;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public class QQMusicAudioTrack extends HttpAudioTrack {
    private final AudioTrackInfo originInfo;
    public QQMusicAudioTrack(AudioTrackInfo trackInfo, MediaContainerDescriptor containerTrackFactory, HttpAudioSourceManager sourceManager) {
        super(new AudioTrackInfo(
                trackInfo.title,
                trackInfo.author,
                trackInfo.length,
                trackInfo.uri,
                false,
                trackInfo.uri,
                trackInfo.artworkUrl,
                trackInfo.isrc
        ), containerTrackFactory, sourceManager);
        originInfo = trackInfo;
    }

    @Override
    public AudioTrackInfo getInfo() {
        return originInfo;
    }
}
