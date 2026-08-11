package dev.felnull.imp.client.music.sourceManager;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.felnull.imp.client.music.audioTrack.QQMusicAudioTrack;
import dev.felnull.imp.client.qqMusic.QQMusicUtil;

import java.util.Objects;

public class QQMusicAudioSourceManager extends HttpAudioSourceManager {
    @Override
    public String getSourceName() {
        return "qq_music";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        if(!reference.identifier.contains("qq_music:"))return null;
        var id = reference.identifier.substring(9);
        long musicID;
        try{
            musicID = Long.parseLong(id);
        } catch (NumberFormatException e) {
            return null;
        }
        var data = QQMusicUtil.getSongData(musicID);
        if(data == null)return null;
        var url = QQMusicUtil.getMusicUrl(data.getMID());
        if(url == null)return null;
        var info = new AudioTrackInfo(
                data.getName(),
                data.getSingerString(),
                Objects.requireNonNull(data.getSecond()) * 1000,
                reference.identifier,
                false,
                url
        );
        var newR = new AudioReference(url, reference.title, reference.containerDescriptor);
        var result = (HttpAudioTrack)super.loadItem(manager, newR);
        return new QQMusicAudioTrack(info, result.getContainerTrackFactory(), this);
    }
}
