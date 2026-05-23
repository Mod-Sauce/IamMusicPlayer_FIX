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
import org.apache.http.HttpRequestInterceptor;

import java.io.IOException;

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
        // Bilibili requires specifying the source, so add a SourceManager.
        configureBuilder(httpClientBuilder -> {
            httpClientBuilder.addInterceptorLast((HttpRequestInterceptor) (request, context) -> {
                String host = request.getFirstHeader("Host") != null
                        ? request.getFirstHeader("Host").getValue()
                        : null;

                if (host != null && host.contains("bilivideo.com")) {
                    request.setHeader("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                    request.setHeader("Referer", "https://www.bilibili.com/");
                    request.setHeader("Origin", "https://www.bilibili.com");
                } else {
                    request.setHeader("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                }
            });
        });
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        if(!reference.identifier.contains("bilibili:"))return null;
        var bv = reference.identifier.substring(9);
        var data = BiliBiliUtil.getMusicMediaResultFromBV(bv);
        if(data == null)return null;
        String url;
        try {
            url = BiliBiliUtil.fetchAudioUrl(bv);
        } catch (IOException | InterruptedException e) {
            return null;
        }
        var info = new AudioTrackInfo(
                data.name(),
                data.author(),
                data.source().getDuration(),
                reference.identifier,
                false,
                url
        );
        var newR = new AudioReference(url, reference.title, reference.containerDescriptor);
        var result = (HttpAudioTrack)super.loadItem(manager, newR);
        return new BilibiliAudioTrack(info, result.getContainerTrackFactory(), this);
    }

    @Override
    protected AudioTrack createTrack(AudioTrackInfo trackInfo, MediaContainerDescriptor containerDescriptor) {
        return new BilibiliAudioTrack(trackInfo, containerDescriptor, this);
    }
}