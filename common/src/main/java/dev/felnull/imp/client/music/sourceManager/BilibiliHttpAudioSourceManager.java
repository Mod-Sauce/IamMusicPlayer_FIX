package dev.felnull.imp.client.music.sourceManager;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import org.apache.http.HttpRequestInterceptor;

public class BilibiliHttpAudioSourceManager extends HttpAudioSourceManager {

    public BilibiliHttpAudioSourceManager() {
        super();
        configureBilibiliHeaders();
    }

    public BilibiliHttpAudioSourceManager(MediaContainerRegistry containerRegistry) {
        super(containerRegistry);
        configureBilibiliHeaders();
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
}