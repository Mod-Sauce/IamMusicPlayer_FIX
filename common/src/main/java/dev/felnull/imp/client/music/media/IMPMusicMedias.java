package dev.felnull.imp.client.music.media;

import com.google.common.collect.ImmutableMap;
import dev.architectury.platform.Platform;

import java.util.Map;
import java.util.TreeMap;

public class IMPMusicMedias {
    protected static final Map<String, MusicMedia> MEDIAS = new TreeMap<>();
    public static final YoutubeMusicMedia YOUTUBE = new YoutubeMusicMedia("youtube");
    public static final SoundCloudMusicMedia SOUNDCLOUD = new SoundCloudMusicMedia("soundcloud");
    public static final HttpMusicMedia HTTP = new HttpMusicMedia("http");
    public static final NeteaseMusicMedia NETEASE_MUSIC = new NeteaseMusicMedia("netease");
    public static final BiliBiliMedia BILIBILI = new BiliBiliMedia("bilibili");
    public static final QQMusicMedia QQ_MUSIC = new QQMusicMedia("qq_music");

    public static void init() {
        // todo The YouTube feature is not available in the NeoForge development environment.
        if(!Platform.isDevelopmentEnvironment() || !Platform.isForgeLike())
            register("youtube", YOUTUBE);
        register("soundcloud", SOUNDCLOUD);
        register("http", HTTP);
        register("netease", NETEASE_MUSIC);
        register("bilibili", BILIBILI);
        register("qq_music", QQ_MUSIC);
    }

    public static void register(String name, MusicMedia media) {
        MEDIAS.put(name, media);
    }

    public static MusicMedia getMedia(String name) {
        synchronized (MEDIAS) {
            return MEDIAS.get(name);
        }
    }

    public static Map<String, MusicMedia> getAllMedia() {
        synchronized (MEDIAS) {
            return ImmutableMap.copyOf(MEDIAS);
        }
    }
}
