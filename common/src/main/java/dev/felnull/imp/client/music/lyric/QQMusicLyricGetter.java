package dev.felnull.imp.client.music.lyric;

import dev.felnull.imp.client.cache.LyricCacheManager;
import dev.felnull.imp.client.qqMusic.QQMusicUtil;
import dev.felnull.imp.music.resource.Lyric;
import dev.felnull.imp.music.resource.MusicSource;

public class QQMusicLyricGetter implements LyricGetter{
    private GetLyricThread thread;

    @Override
    public void run(MusicSource musicSource) {
        thread = new GetLyricThread(musicSource);
        thread.start();
    }

    @Override
    public void runAndWait(MusicSource musicSource) {
        run(musicSource);
        try {thread.join();} catch (InterruptedException ignored) {}
    }

    @Override
    public void stop() {
        thread.interrupt();
    }

    @Override
    public boolean isFinish() {
        return !thread.isAlive();
    }

    @Override
    public Lyric getLyric() {
        return thread.lyric;
    }

    private static class GetLyricThread extends Thread{
        private final MusicSource source;
        public volatile Lyric lyric;

        public GetLyricThread(MusicSource source){
            this.source = source;
        }

        @Override
        public void run() {
            lyric = QQMusicUtil.getLyric(source.getIdentifier());
            LyricCacheManager.cacheAsync(source.getLoaderType() + "_" + source.getIdentifier(), lyric);
        }
    }
}
