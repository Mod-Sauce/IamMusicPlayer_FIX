package dev.felnull.imp.client.music.loader;

import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.client.lava.LavaPlayerManager;
import dev.felnull.imp.client.music.MusicEngine;
import dev.felnull.imp.client.music.player.LavaMusicPlayer;
import dev.felnull.imp.client.music.player.MusicPlayer;
import dev.felnull.imp.client.webdav.proxy.WebDAVProxyStreamManager;
import dev.felnull.imp.music.resource.MusicSource;
import dev.felnull.imp.networking.IMPPackets;
import dev.felnull.imp.webdav.WebDAVSourceUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.UUID;

public class WebDAVProxyMusicLoader implements MusicLoader {
    private static final LocalAudioSourceManager MANAGER = new LocalAudioSourceManager();
    private MusicSource musicSource;
    private AudioTrack audioTrack;

    @Override
    public @NotNull MusicPlayer<?, ?> createMusicPlayer(UUID musicPlayerId) {
        return new LavaMusicPlayer(musicPlayerId, audioTrack, musicSource);
    }

    @Override
    public void tryLoad(@NotNull MusicSource source) throws Exception {
        if (!WebDAVSourceUtil.PROXY_LOADER_TYPE.equals(source.getLoaderType())) {
            throw new RuntimeException("Unsupported media");
        }
        UUID sessionId = UUID.fromString(source.getIdentifier());
        MusicEngine.getInstance().getLogger().info("[WebDAVProxy] Requesting proxy session {}", sessionId);
        NetworkManager.sendToServer(IMPPackets.WEBDAV_PROXY_REQUEST_CTS, new IMPPackets.WebDAVProxyRequestMessage(sessionId).toRFBB());
        Path file = WebDAVProxyStreamManager.await(sessionId);
        MusicEngine.getInstance().getLogger().info("[WebDAVProxy] Received proxied file {} for session {}", file, sessionId);
        var result = MANAGER.loadItem(LavaPlayerManager.getInstance().getAudioPlayerManager(), new AudioReference(file.toString(), ""));
        if (!(result instanceof AudioTrack track)) throw new RuntimeException("Failed to load proxied WebDAV track");
        this.audioTrack = track;
        this.musicSource = source;
    }

    @Override
    public int priority() {
        return 200000;
    }
}
