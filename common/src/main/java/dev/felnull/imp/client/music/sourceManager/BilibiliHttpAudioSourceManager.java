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
        configureBuilder(httpClientBuilder -> {
            httpClientBuilder.addInterceptorLast((HttpRequestInterceptor) (request, context) -> {
                String host = request.getFirstHeader("Host") != null
                        ? request.getFirstHeader("Host").getValue()
                        : null;

                // 判断是否为 B 站 CDN 域名
                if (host != null && host.contains("bilivideo.com")) {
                    request.setHeader("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                    request.setHeader("Referer", "https://www.bilibili.com/");
                    request.setHeader("Origin", "https://www.bilibili.com");
                } else {
                    // 对其他请求也设置一个常见的 UA，避免被服务器拒绝
                    request.setHeader("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                }
            });
        });
    }
}