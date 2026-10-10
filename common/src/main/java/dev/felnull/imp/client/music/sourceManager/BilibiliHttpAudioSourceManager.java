package dev.felnull.imp.client.music.sourceManager;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.felnull.imp.client.bilibili.BiliBiliUtil;
import dev.felnull.imp.client.music.audioTrack.BilibiliAudioTrack;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.client.music.netmusic.NetMusicUtil;
import org.apache.http.HttpRequestInterceptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BilibiliHttpAudioSourceManager extends HttpAudioSourceManager {

    public BilibiliHttpAudioSourceManager() {
        super();
        configureBilibiliHeaders();
    }

    @Override
    public String getSourceName() {
        return "bilibili";
    }

    private void configureBilibiliHeaders() {
        // Bilibili only serves the CDN when the request carries browser UA/Referer/Origin headers; the set is shared
        // with the audio-address probe below so the two can never disagree.
        configureBuilder(httpClientBuilder -> {
            httpClientBuilder.addInterceptorLast((HttpRequestInterceptor) (request, context) -> {
                String host = request.getFirstHeader("Host") != null
                        ? request.getFirstHeader("Host").getValue()
                        : "";
                BiliBiliUtil.cdnHeaders("https://" + host + "/").forEach(request::setHeader);
            });
        });
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        // [logging only] All three "return null" spots below get a line: without them the log only shows
        // "No available loaders found". The label names the manager, not the identifier — this one is asked
        // about **every** source (the netease/kugou ones reach it too) and says so instead of "BiliBili …".
        dev.felnull.imp.client.music.link.LinkDebug.log("sourceManager loadItem", "manager", "bilibili",
                "identifier", reference.identifier);
        if(!reference.identifier.contains("bilibili:"))return null;
        var bvRaw = reference.identifier.substring(9);
        var bv = BiliBiliUtil.stripPage(bvRaw);   // the address API takes the bare video number (the part is expressed by the page parameter)
        var page = BiliBiliUtil.pageOf(bvRaw);    // part (1 when the identifier has no -p<N>)
        // Media info must be fetched with the **original identifier including -pN** (the method picks duration/title from
        // that part), while the audio stream below uses the stripped `page` — otherwise the two disagree on multi-part videos.
        var data = BiliBiliUtil.getMusicMediaResultFromBV(bvRaw);
        if(data == null){
            dev.felnull.imp.client.music.link.LinkDebug.log("BiliBili media info NULL", "bv", bv);
            // Fallback only: a concrete reason recorded internally must not be overwritten with a generic line.
            dev.felnull.imp.client.music.MusicLoadFailure.noteReasonIfAbsent(bvRaw,
                    "B站取不到这个视频的信息（接口异常，详见日志）");
            return null;
        }
        Pick pick;
        List<String> candidates;
        try {
            // Ranked addresses of the same stream (backupUrl = another CDN), so a dead edge is skipped rather than retried.
            candidates = BiliBiliUtil.fetchAudioCandidates(bv, page, null);
            pick = pickReachable(bv, candidates);
        } catch (IOException | InterruptedException e) {
            dev.felnull.imp.client.music.link.LinkDebug.log("BiliBili fetchAudioUrl FAIL", "bv", bv,
                    "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
            // The same reason is sent to the UI as well (an actionable notice such as 412 = risk control should not just sit in the log)
            dev.felnull.imp.client.music.MusicLoadFailure.noteReason(bvRaw,
                    BiliBiliUtil.fetchFailReason(e));
            return null;
        }
        var url = pick.url();
        // [logging only] The host is the trace that matters here: the default answer points at a P2P edge whose first
        // connect times out, and the chosen address is what says whether the failover did its job.
        dev.felnull.imp.client.music.link.LinkDebug.log("BiliBili url OK", "bv", bv, "page", page,
                "name", data.name(), "duration", data.source().getDuration(),
                "url", LinkDebug.safeUrl(url), "probe", pick.probe());
        // [Audio-address probe] An address whose CDN answers with a non-audio page (charged part, expired sign, risk
        // control) would play silently; the probe is a plain HttpURLConnection, so the host-agnostic browser headers
        // must be passed explicitly (a header-less request gets HTTP 403 + HTML from every CDN family).
        var probe = pick.probe();
        if (probe == NetMusicUtil.AudioProbe.NOT_AUDIO) {
            dev.felnull.imp.client.music.MusicLoadFailure.noteReason(bvRaw,
                    "B站这个地址不是音频（可能是付费或受限视频）");
            dev.felnull.imp.client.music.link.LinkDebug.log("BiliBili audio probe FAIL", "bv", bv,
                    "reason", NetMusicUtil.describeProbe(url), "probe", probe);
            return null;
        }
        if (probe == NetMusicUtil.AudioProbe.REJECTED) {
            // REJECTED = the CDN refused our request (401/403/412): a header/identity problem, **not** proof of a
            // paid video — reporting it as "不是音频" sent the investigation the wrong way.
            dev.felnull.imp.client.music.MusicLoadFailure.noteReason(bvRaw,
                    "B站 CDN 拒绝了这次请求（403/412）：多半是请求头/风控问题，不是付费视频");
            dev.felnull.imp.client.music.link.LinkDebug.log("BiliBili audio probe REJECTED", "bv", bv,
                    "reason", NetMusicUtil.describeProbe(url), "probe", probe);
            return null;
        }
        if (probe == NetMusicUtil.AudioProbe.UNKNOWN) {
            dev.felnull.imp.client.music.link.LinkDebug.log("BiliBili audio probe UNKNOWN", "bv", bv,
                    "note", "no candidate answered in time; the fetch still goes ahead", "url", LinkDebug.safeUrl(url));
        }
        var info = new AudioTrackInfo(
                data.name(),
                data.author(),
                data.source().getDuration(),
                reference.identifier,
                false,
                url
        );
        // 逐个试：Hi-Res(FLAC) 在最前，LavaPlayer 认不出它时自动退到后面的 AAC ——
        // 一首歌不该因为"B站给了 Hi-Res"就直接加载失败。
        var attempts = new ArrayList<String>();
        attempts.add(url);
        for (var c : candidates) {
            if (!attempts.contains(c))
                attempts.add(c);
        }
        int maxTries = Math.min(4, attempts.size());
        for (int i = 0; i < maxTries; i++) {
            var tryUrl = attempts.get(i);
            // Not a plain cast: `super.loadItem` returns null when it cannot get an audio stream, and casting that throws.
            var item = super.loadItem(manager, new AudioReference(tryUrl, reference.title, reference.containerDescriptor));
            if (item instanceof HttpAudioTrack httpTrack) {
                if (i > 0)
                    dev.felnull.imp.client.music.link.LinkDebug.log("BiliBili audio fallback OK", "bv", bv,
                            "try", i, "of", maxTries, "url", LinkDebug.safeUrl(tryUrl));
                return new BilibiliAudioTrack(info, httpTrack.getContainerTrackFactory(), this);
            }
            dev.felnull.imp.client.music.link.LinkDebug.log("BiliBili httpItem FAIL", "bv", bv,
                    "try", i, "of", maxTries, "url", tryUrl.split("\\?")[0]);
        }
        dev.felnull.imp.client.music.MusicLoadFailure.noteReason(bvRaw,
                "B站地址 LavaPlayer 不认（404 或不是音频流）");
        return null;
    }

    @Override
    protected AudioTrack createTrack(AudioTrackInfo trackInfo, MediaContainerDescriptor containerDescriptor) {
        return new BilibiliAudioTrack(trackInfo, containerDescriptor, this);
    }

    /** The address a load will use, plus the probe verdict it was chosen on. */
    private record Pick(String url, NetMusicUtil.AudioProbe probe) {
    }

    /**
     * The first candidate that answers, keeping the order when every candidate is unreachable (a timeout proves nothing); a candidate that is not audio never wins, so an expired address cannot take a working one's slot.
     */
    private Pick pickReachable(String bv, List<String> candidates) {
        var worst = NetMusicUtil.AudioProbe.OK;
        for (var i = 0; i < candidates.size(); i++) {
            var candidate = candidates.get(i);
            var probe = NetMusicUtil.probeAudioUrl(candidate, BiliBiliUtil.cdnHeaders(candidate));
            // One line per probe: this is what tells a dead edge from a slow one in the log.
            LinkDebug.log("BiliBili cdn probe", "bv", bv, "rank", i, "of", candidates.size(),
                    "probe", probe, "url", LinkDebug.safeUrl(candidate));
            if (probe == NetMusicUtil.AudioProbe.OK) {
                // A working lower-ranked host is used now and picked first on the next load as well.
                if (i > 0) candidates.add(0, candidates.remove(i));
                return new Pick(candidate, probe);
            }
            if (severityOf(probe) > severityOf(worst))
                worst = probe;
        }
        LinkDebug.log("BiliBili cdn pick", "bv", bv, "rank", 0, "probe", worst,
                "url", LinkDebug.safeUrl(candidates.get(0)), "candidates", candidates.size());
        return new Pick(candidates.get(0), worst);
    }

    /** How conclusive a probe verdict is; the most conclusive one decides the failure message. */
    private static int severityOf(NetMusicUtil.AudioProbe probe) {
        return switch (probe) {
            case OK -> 0;
            case UNKNOWN -> 1;
            case REJECTED -> 2;
            case NOT_AUDIO -> 3;
        };
    }
}