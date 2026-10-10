package dev.felnull.imp.client.music.sourceManager;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.felnull.imp.client.kugou.KuGouUtil;
import dev.felnull.imp.client.music.MusicLoadFailure;
import dev.felnull.imp.client.music.audioTrack.KuGouAudioTrack;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.client.music.netmusic.NetMusicUtil;

/**
 * The LavaPlayer source manager for KuGou music: resolves {@code "kugou:<hash>"} into a currently usable audio address.
 */
public class KuGouAudioSourceManager extends HttpAudioSourceManager {
    private static final String PREFIX = "kugou:";

    @Override
    public String getSourceName() {
        return "kugou";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        if (reference.identifier == null || !reference.identifier.startsWith(PREFIX))
            return null;

        var hash = KuGouUtil.normalizeHash(reference.identifier.substring(PREFIX.length()));
        if (hash == null) {
            LinkDebug.log("KuGou loadItem", "identifier", reference.identifier, "reason", "bad hash");
            return null;
        }

        // ⚠️ The "failure reason" must be recorded with the **bare identifier** (= the part after the prefix), not `reference.identifier`
        // (`kugou:<hash>`, the prefix added by `KuGouMusicLoader#getIdentifier`): whoever looks the reason up in `MusicEntry`
        // (the log's `reason=`) and on the remote playback page (the red UI text) uses `MusicSource#getIdentifier()`.
        var failId = reference.identifier.substring(PREFIX.length());

        var info = KuGouUtil.getSongInfo(hash);
        if (info == null) {
            MusicLoadFailure.noteReason(failId, "酷狗接口没返回这首歌的信息");
            return null;
        }
        // The address goes through the **signed API** first (with device/login state; paid tracks only stand a chance on that route), falling back to the public API on failure.
        // See KuGouUtil#fetchAudioUrl -- that one also handles "auto downgrade when a non-128 quality is unavailable".
        var resolveDetail = new StringBuilder();
        var url = KuGouUtil.fetchAudioUrl(hash, resolveDetail);
        if (url == null) {
            // The reason must be built from what is actually known: a fixed "needs login" wording is wrong once a token is configured.
            // `describeFailure` combines the login state with the platform's own words and the account's VIP state (a lapsed daily VIP is the usual cause of fail=["pkg","buy"]).
            var vip = KuGouUtil.vipSummary();
            MusicLoadFailure.noteReason(failId,
                    KuGouUtil.describeFailure(info.error(), resolveDetail.toString(), KuGouUtil.hasLogin(), vip));
            LinkDebug.log("KuGou loadItem FAILED", "hash", hash, "error", info.error(),
                    "resolve", resolveDetail.toString(), "hasLogin", KuGouUtil.hasLogin(), "vip", vip);
            return null;
        }

        var trackInfo = new AudioTrackInfo(
                info.name() == null ? hash : info.name(),
                info.author() == null ? "" : info.author(),
                info.durationMs(),
                reference.identifier,
                false,
                url
        );
        // [Audio-address probe] An address was resolved, but that does not mean it is audio: a paid/restricted track can come back
        // as an error page (HTTP 200), which LavaPlayer only notices at decode time — that is exactly the "shows as playing, no sound for a few seconds" bug.
        // The probe is the same lightweight GET+Range check as `probeUrl` below, just evaluated as a verdict instead of written as a log line.
        var probe = NetMusicUtil.probeAudioUrl(url);
        if (probe == NetMusicUtil.AudioProbe.NOT_AUDIO) {
            MusicLoadFailure.noteReason(failId, "酷狗这个地址不是音频（受限曲目需登录）");
            LinkDebug.log("KuGou loadItem FAILED", "hash", hash, "reason", "audio probe not audio",
                    "hasLogin", KuGouUtil.hasLogin());
            probeUrl(hash, url);
            return null;
        }
        var httpItem = super.loadItem(manager, new AudioReference(url, reference.title, reference.containerDescriptor));
        if (!(httpItem instanceof HttpAudioTrack httpTrack)) {
            // HttpAudioSourceManager probes/downloads this address with **Java's HttpURLConnection** and returns null when it cannot get the audio;
            // the reason is recorded here, otherwise "address resolved but cannot play" has no lead in the log.
            MusicLoadFailure.noteReason(failId, "地址取到了但拉不到音频流（CDN 拒绝或返回了错误页）");
            probeUrl(hash, url);
            return null;
        }
        LinkDebug.log("KuGou loadItem OK", "hash", hash, "name", info.name(),
                "url", LinkDebug.safeUrl(url));
        return new KuGouAudioTrack(trackInfo, httpTrack.getContainerTrackFactory(), this);
    }

    /**
     * Diagnostics: send a Range request straight at this audio address and record the HTTP status and Content-Type.
     */
    private static void probeUrl(String hash, String url) {
        try {
            var conn = (java.net.HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Range", "bytes=0-0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();
            var type = conn.getContentType();
            var len = conn.getContentLengthLong();
            conn.disconnect();
            LinkDebug.log("KuGou url probe", "hash", hash, "httpStatus", code,
                    "contentType", type, "contentLength", len);
        } catch (Exception e) {
            LinkDebug.log("KuGou url probe EX", "hash", hash,
                    "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
