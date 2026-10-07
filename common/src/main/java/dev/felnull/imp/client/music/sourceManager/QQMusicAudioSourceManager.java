package dev.felnull.imp.client.music.sourceManager;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.felnull.imp.client.music.MusicLoadFailure;
import dev.felnull.imp.client.music.audioTrack.QQMusicAudioTrack;
import dev.felnull.imp.client.music.link.LinkDebug;
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
        // `id` = the **bare identifier** with the source prefix removed; it is always used to record the "failure reason" — whoever looks it up
        // in `MusicEntry` and on the remote playback page uses `MusicSource#getIdentifier()`, so writing the prefixed `reference.identifier` would never be found.
        var id = reference.identifier.substring(9);
        long musicID;
        try{
            musicID = Long.parseLong(id);
        } catch (NumberFormatException e) {
            return null;
        }
        var data = QQMusicUtil.getSongData(musicID);
        if(data == null){
            MusicLoadFailure.noteReason(id, "QQ 拿不到这首歌的数据（多半 Cookie 失效）");
            return null;
        }
        var url = QQMusicUtil.getMusicUrl(data.getMID());
        if(url == null){
            MusicLoadFailure.noteReason(id, "QQ 没给播放地址（多半 Cookie 失效或曲目受限）");
            return null;
        }

        // [Shell address] When QQ cannot get a playback address it returns `sip + ""`, which concatenates into `http://aqqmusic.tc.qq.com/`
        // — a domain with no path. ⚠️ Handing that to LavaPlayer only turns into **silent playback** (the speaker runs, nothing is heard),
        // so it must be failed outright here instead.
        if (QQMusicUtil.isShellUrl(url)) {
            MusicLoadFailure.noteReason(id, "QQ 只回了空壳地址（多半 Cookie 失效或被限）");
            LinkDebug.log("QQ loadItem FAIL", "songId", musicID, "mid", data.getMID(),
                    "reason", "shell url（多半是没填 Cookie / Cookie 不完整 / 曲目受限）", "urlHost", LinkDebug.safeUrl(url));
            return null;
        }

        // [Duration] Same reasoning as getMusicMediaResult: 0/empty means no track is created (duration 0 would be judged "already finished" and destroyed)
        var second = data.getSecond();
        if (second == null || second <= 0) {
            MusicLoadFailure.noteReason(id, "QQ 没给时长（时长为 0 会被判成已播完）");
            LinkDebug.log("QQ loadItem FAIL", "songId", musicID, "mid", data.getMID(),
                    "reason", "no duration", "second", second);
            return null;
        }

        var info = new AudioTrackInfo(
                data.getName(),
                data.getSingerString(),
                second * 1000,
                reference.identifier,
                false,
                url
        );
        var newR = new AudioReference(url, reference.title, reference.containerDescriptor);
        var result = (HttpAudioTrack)super.loadItem(manager, newR);
        // [logging] ⚠️ The URL logged here must be **host+path only, no query**: it carries credentials such as vkey.
        if (result == null) {
            MusicLoadFailure.noteReason(id, "QQ 的地址 LavaPlayer 不认（多半 404 或不是音频）");
            LinkDebug.log("QQ loadItem FAIL", "songId", musicID, "mid", data.getMID(),
                    "reason", "LavaPlayer(HttpAudioSourceManager) 没认这个地址（多半 404/非音频）",
                    "urlHost", LinkDebug.safeUrl(url), "durationSec", second);
            return null;
        }
        LinkDebug.log("QQ loadItem OK", "songId", musicID, "mid", data.getMID(),
                "urlHost", LinkDebug.safeUrl(url), "durationSec", second);
        return new QQMusicAudioTrack(info, result.getContainerTrackFactory(), this);
    }
}
