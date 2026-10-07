package dev.felnull.imp.client.music.audioTrack;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

/** KuGou music audio track: the base is an HTTP track (what actually plays is an expiring direct link), but it always
 * reports the **original identifier** ({@code kugou:<hash>}) instead of that temporary address — otherwise IMP would
 * store the expired address as the music identifier. Same handling as {@link QQMusicAudioTrack}. */
public class KuGouAudioTrack extends HttpAudioTrack {
    private final AudioTrackInfo originInfo;

    public KuGouAudioTrack(AudioTrackInfo trackInfo, MediaContainerDescriptor containerTrackFactory, HttpAudioSourceManager sourceManager) {
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
