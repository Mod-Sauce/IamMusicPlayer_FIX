package dev.felnull.imp.server.webdav;

import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.music.resource.MusicSource;
import dev.felnull.imp.networking.IMPPackets;
import dev.felnull.imp.server.saveddata.WebDAVProfileSaveData;
import dev.felnull.imp.webdav.WebDAVSourceUtil;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import org.apache.http.HttpHeaders;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ServerWebDAVProxyManager {

    private static final Logger LOGGER = LogManager.getLogger(
        ServerWebDAVProxyManager.class
    );
    private static final Map<UUID, ProxySession> SESSIONS =
        new ConcurrentHashMap<>();
    private static final int CHUNK_SIZE = 24 * 1024;

    private ServerWebDAVProxyManager() {}

    public static void updateProfile(
        ServerPlayer player,
        String baseUrl,
        String rootPath,
        String username,
        String password
    ) {
        WebDAVProfileSaveData.get(player.server).setProfile(
            player.getUUID(),
            new ServerWebDAVProfile(
                baseUrl,
                rootPath,
                username,
                ServerWebDAVCrypto.encrypt(password == null ? "" : password)
            )
        );
    }

    public static IMPPackets.MusicReadyMessage createReadyMessage(
        ServerPlayer listener,
        UUID waitId,
        UUID ringerId,
        MusicSource source,
        net.minecraft.nbt.CompoundTag tracker,
        long position
    ) {
        if (!WebDAVSourceUtil.isWebDAV(source)) {
            return new IMPPackets.MusicReadyMessage(
                waitId,
                ringerId,
                source,
                tracker,
                position
            );
        }
        UUID owner = WebDAVSourceUtil.extractOwner(source.getIdentifier());
        String path = WebDAVSourceUtil.extractPath(source.getIdentifier());
        if (owner == null) {
            owner = listener.getUUID();
        }
        UUID sessionId = UUID.randomUUID();
        SESSIONS.put(
            sessionId,
            new ProxySession(sessionId, owner, path, source.getDuration())
        );
        return new IMPPackets.MusicReadyMessage(
            waitId,
            ringerId,
            new MusicSource(
                WebDAVSourceUtil.PROXY_LOADER_TYPE,
                sessionId.toString(),
                source.getDuration()
            ),
            tracker,
            position
        );
    }

    public static void streamToClient(ServerPlayer player, UUID sessionId) {
        ProxySession session = SESSIONS.get(sessionId);
        if (session == null) {
            NetworkManager.sendToPlayer(
                player,
                IMPPackets.WEBDAV_PROXY_ERROR_STC,
                new IMPPackets.WebDAVProxyErrorMessage(
                    sessionId,
                    "Missing WebDAV session"
                ).toRFBB()
            );
            return;
        }
        ServerWebDAVProfile profile = WebDAVProfileSaveData.get(
            player.server
        ).getProfile(session.ownerId());
        if (profile == null) {
            NetworkManager.sendToPlayer(
                player,
                IMPPackets.WEBDAV_PROXY_ERROR_STC,
                new IMPPackets.WebDAVProxyErrorMessage(
                    sessionId,
                    "Owner has no server WebDAV profile"
                ).toRFBB()
            );
            return;
        }
        try (CloseableHttpClient client = createClient(profile)) {
            String url = joinUrl(
                profile.getBaseUrl(),
                buildDavPath(profile.getRootPath(), session.relativePath())
            );
            HttpGet request = new HttpGet(url);
            applyAuthHeader(request, profile);
            try (CloseableHttpResponse response = client.execute(request)) {
                if (
                    response.getEntity() == null ||
                    response.getStatusLine().getStatusCode() >= 400
                ) {
                    NetworkManager.sendToPlayer(
                        player,
                        IMPPackets.WEBDAV_PROXY_ERROR_STC,
                        new IMPPackets.WebDAVProxyErrorMessage(
                            sessionId,
                            "WebDAV download failed"
                        ).toRFBB()
                    );
                    return;
                }
                long totalSize = response.getEntity().getContentLength();
                NetworkManager.sendToPlayer(
                    player,
                    IMPPackets.WEBDAV_PROXY_START_STC,
                    new IMPPackets.WebDAVProxyStartMessage(
                        sessionId,
                        totalSize,
                        session.relativePath()
                    ).toRFBB()
                );
                try (InputStream in = response.getEntity().getContent()) {
                    byte[] buffer = new byte[CHUNK_SIZE];
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        byte[] chunk = buffer;
                        if (read != buffer.length) {
                            chunk = new byte[read];
                            System.arraycopy(buffer, 0, chunk, 0, read);
                        }
                        NetworkManager.sendToPlayer(
                            player,
                            IMPPackets.WEBDAV_PROXY_CHUNK_STC,
                            new IMPPackets.WebDAVProxyChunkMessage(
                                sessionId,
                                chunk
                            ).toRFBB()
                        );
                    }
                }
                NetworkManager.sendToPlayer(
                    player,
                    IMPPackets.WEBDAV_PROXY_END_STC,
                    new IMPPackets.WebDAVProxyEndMessage(sessionId).toRFBB()
                );
            }
        } catch (Exception e) {
            LOGGER.error(
                "[WebDAVProxy] streamToClient failed session={}",
                sessionId,
                e
            );
            NetworkManager.sendToPlayer(
                player,
                IMPPackets.WEBDAV_PROXY_ERROR_STC,
                new IMPPackets.WebDAVProxyErrorMessage(
                    sessionId,
                    e.getMessage() == null
                        ? "Unknown WebDAV error"
                        : e.getMessage()
                ).toRFBB()
            );
        }
    }

    private static CloseableHttpClient createClient(
        ServerWebDAVProfile profile
    ) {
        BasicCredentialsProvider provider = new BasicCredentialsProvider();
        if (
            !profile.getUsername().isBlank() ||
            !profile.getEncryptedPassword().isBlank()
        ) {
            provider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(
                    profile.getUsername(),
                    profile.getPassword()
                )
            );
        }
        return HttpClients.custom()
            .setDefaultCredentialsProvider(provider)
            .build();
    }

    private static void applyAuthHeader(
        HttpRequestBase request,
        ServerWebDAVProfile profile
    ) {
        if (
            profile.getUsername().isBlank() && profile.getPassword().isBlank()
        ) return;
        var token = Base64.getEncoder().encodeToString(
            (profile.getUsername() + ":" + profile.getPassword()).getBytes(
                StandardCharsets.UTF_8
            )
        );
        request.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + token);
    }

    private static String buildDavPath(String rootPath, String relativePath) {
        String root = normalize(rootPath);
        String path = normalize(relativePath);
        String combined = root.isEmpty()
            ? path
            : path.isEmpty()
              ? root
              : root + "/" + path;
        if (combined.isEmpty()) return "/";
        return "/" + encodePath(combined);
    }

    private static String normalize(String path) {
        if (path == null || path.isBlank()) return "";
        String normalized = path.replace('\\', '/').trim();
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/"))
            normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    private static String encodePath(String path) {
        String[] parts = path.split("/");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (builder.length() > 0) builder.append('/');
            builder.append(
                URLEncoder.encode(part, StandardCharsets.UTF_8).replace(
                    "+",
                    "%20"
                )
            );
        }
        return builder.toString();
    }

    private static String joinUrl(String baseUrl, String path) {
        String normalizedBase = baseUrl.endsWith("/")
            ? baseUrl.substring(0, baseUrl.length() - 1)
            : baseUrl;
        return normalizedBase + path;
    }

    private record ProxySession(
        UUID sessionId,
        UUID ownerId,
        String relativePath,
        long duration
    ) {}
}
