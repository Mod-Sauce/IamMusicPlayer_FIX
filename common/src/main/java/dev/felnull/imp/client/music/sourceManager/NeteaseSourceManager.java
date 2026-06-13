package dev.felnull.imp.client.music.sourceManager;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.audioTrack.NeteaseAudioTrack;
import dev.felnull.imp.client.music.netmusic.NetMusicUtil;

import java.io.DataInput;
import java.io.DataOutput;
import java.net.URL;

public class NeteaseSourceManager extends HttpAudioSourceManager {
    private final HttpAudioSourceManager http = new HttpAudioSourceManager();
    public HttpInterface getHttpInterface() {
        return http.getHttpInterface();
    }

    @Override
    public String getSourceName() {
        return "netease";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        long songId;
        if(!reference.identifier.contains("netease:"))return null;
        var id = reference.identifier.substring(8);
        try {
            songId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            return null;
        }
        var data = NetMusicUtil.getNetMusicJson(songId);
        var infoData = NetMusicUtil.getNetMusicSongData(data);
        URL url = NetMusicUtil.getNetMusicUrl(songId);
        if(infoData == null || url == null)
            return null;
        var name = IamMusicPlayer.getConfig().netMusicConfig.withTransName && !infoData.getTransName().isEmpty() ?
                String.format("%s(%s)", infoData.getName(), infoData.getTransName()) :
                infoData.getName();
        var info = new AudioTrackInfo(
                name,
                String.join("、", infoData.getArtists()),
                infoData.getDuration(),
                reference.identifier,
                false,
                url.toString(),
                null, null
        );
        return new NeteaseAudioTrack(info, this);
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) {

    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) {
        return new NeteaseAudioTrack(trackInfo, this);
    }

    @Override
    public void shutdown() {
        http.shutdown();
    }
}
