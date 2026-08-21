package dev.felnull.imp.client.music.sourceManager;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.audioTrack.WebDAVAudioTrack;
import dev.felnull.imp.client.webdav.WebDAVUtil;
import dev.felnull.imp.util.ProxyUtil;
import dev.felnull.imp.webdav.WebDAVSourceUtil;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class WebDAVAudioSourceManager extends HttpAudioSourceManager {
    public WebDAVAudioSourceManager() {
        super();
        configureClient();
    }

    @Override
    public String getSourceName() {
        return "webdav";
    }

    private void configureClient() {
        var config = IamMusicPlayer.getConfig().webDAVConfig;
        var authorization = (!config.username.isBlank() || !config.password.isBlank())
                ? "Basic " + Base64.getEncoder().encodeToString((config.username + ":" + config.password).getBytes(StandardCharsets.UTF_8))
                : null;

        configureBuilder(httpClientBuilder -> {
            var netProxy = ProxyUtil.getProxy();
            if (netProxy.type() == Proxy.Type.HTTP) {
                SocketAddress addr = netProxy.address();
                if (addr instanceof InetSocketAddress inetAddr) {
                    httpClientBuilder.setProxy(new HttpHost(inetAddr.getHostString(), inetAddr.getPort()));
                }
            }

            httpClientBuilder.addInterceptorFirst((HttpRequestInterceptor) (request, context) -> {
                if (authorization != null && !request.containsHeader("Authorization")) {
                    request.setHeader("Authorization", authorization);
                }
            });
        });
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        if (!reference.identifier.startsWith("webdav:")) return null;

        var path = WebDAVSourceUtil.extractPath(reference.identifier.substring(7));
        var url = WebDAVUtil.getFileUrl(path);
        if (url == null) return null;

        var metadata = WebDAVUtil.getFileMetadata(path);
        var title = metadata != null ? metadata.name() : WebDAVUtil.getDisplayName(path);
        var info = new AudioTrackInfo(
                title,
                "WebDAV",
                metadata != null ? metadata.size() : 0,
                path,
                false,
                url,
                null,
                null
        );

        var newReference = new AudioReference(url, reference.title, reference.containerDescriptor);
        var result = (HttpAudioTrack) super.loadItem(manager, newReference);
        return new WebDAVAudioTrack(info, result.getContainerTrackFactory(), this);
    }

    @Override
    protected AudioTrack createTrack(AudioTrackInfo trackInfo, MediaContainerDescriptor containerDescriptor) {
        return new WebDAVAudioTrack(trackInfo, containerDescriptor, this);
    }
}
