package dev.felnull.imp.client.music.lyric;

import dev.felnull.imp.music.resource.Lyric;
import dev.felnull.imp.music.resource.MusicSource;

public interface LyricGetter {
    void run(MusicSource musicSource);
    void stop();
    boolean isFinish();
    Lyric getLyric();
}
