package dev.felnull.imp.client.music;

import dev.felnull.imp.music.resource.MusicSource;
import dev.felnull.imp.music.tracker.MusicTracker;
import dev.felnull.imp.networking.IMPPackets;

import java.util.UUID;

public class MusicRingerEngineConnector {
    public static void load(UUID musicPlayerId, MusicTracker musicTracker, MusicSource source, long position, LoadCompleteListener listener) {
        // World playback: the loader may refuse a trial clip here (boombox config), which makes the ring skip the track.
        getEngine().load(musicPlayerId, source, position, listener, true);
        getEngine().addSpeaker(musicPlayerId, musicPlayerId, musicTracker);
    }

    public static void play(UUID musicPlayerId, long delay) {
        getEngine().play(musicPlayerId, delay);
    }

    public static void stop(UUID musicPlayerId) {
        getEngine().stop(musicPlayerId);
    }

    public static IMPPackets.MusicRingResponseStateType update(UUID musicPlayerId, MusicTracker musicTracker) {
        if (getEngine().isPlaying(musicPlayerId)) {
            getEngine().updateMusicTracker(musicPlayerId, musicPlayerId, musicTracker);
            return IMPPackets.MusicRingResponseStateType.PLAYING;
        }

        if (getEngine().isLoading(musicPlayerId) || getEngine().isExist(musicPlayerId))
            return IMPPackets.MusicRingResponseStateType.LOADING;

        return IMPPackets.MusicRingResponseStateType.NONE;
    }

    private static MusicEngine getEngine() {
        return MusicEngine.getInstance();
    }
}
