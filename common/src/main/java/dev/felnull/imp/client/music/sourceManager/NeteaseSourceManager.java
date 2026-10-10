package dev.felnull.imp.client.music.sourceManager;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.MusicLoadFailure;
import dev.felnull.imp.client.music.audioTrack.NeteaseAudioTrack;
import dev.felnull.imp.client.music.link.LinkDebug;
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
        // ⚠️ `id` = the **bare identifier** with the source prefix removed: the "failure reason" must be recorded with this form,
        // because whoever looks it up in the MusicEntry log and on the remote playback page uses `MusicSource#getIdentifier()`.
        var id = reference.identifier.substring(8);
        try {
            songId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            return null;
        }
        var data = NetMusicUtil.getNetMusicJson(songId);
        var infoData = NetMusicUtil.getNetMusicSongData(data);
        URL url = NetMusicUtil.getNetMusicUrl(songId);
        if(infoData == null || url == null){
            // This reason is the one for restricted Netease tracks / not logged in.
            MusicLoadFailure.noteReason(id, "网易云拿不到曲目信息或播放地址（受限曲目需登录；会员曲需要会员账号）");
            return null;
        }
        // [Audio-address probe] A restricted track "resolves" to an address that is not audio: the anonymous API 302s to `music.163.com/404`,
        // which answers HTTP 200 with an HTML page, so LavaPlayer builds an MP3 that only fails while decoding (UI shows "playing", silence).
        if (url.toString().contains("music.163.com/404")) {
            MusicLoadFailure.noteReason(id, "网易云这个地址不是音频（受限曲目需登录；会员曲需要会员账号）");
            LinkDebug.log("netease loadItem FAIL", "id", songId, "reason", "302 to music.163.com/404 (restricted)",
                    "hasCookie", NetMusicUtil.hasCookie(), "url", LinkDebug.safeUrl(url.toString()));
            return null;
        }
        var probe = NetMusicUtil.probeAudioUrl(url.toString());
        if (probe == NetMusicUtil.AudioProbe.NOT_AUDIO) {
            MusicLoadFailure.noteReason(id, "网易云这个地址不是音频（受限曲目需登录；会员曲需要会员账号）");
            LinkDebug.log("netease loadItem FAIL", "id", songId, "reason", "audio probe not audio",
                    "hasCookie", NetMusicUtil.hasCookie(), "url", LinkDebug.safeUrl(url.toString()));
            return null;
        }
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
