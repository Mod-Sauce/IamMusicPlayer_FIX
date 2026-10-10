package dev.felnull.imp.client.music.lyric;

import dev.felnull.imp.client.cache.LyricCacheManager;
import dev.felnull.imp.client.kugou.KuGouUtil;
import dev.felnull.imp.music.resource.Lyric;
import dev.felnull.imp.music.resource.MusicSource;

/**
 * The KuGou lyrics getter (like the Netease / bilibili / QQ ones): spawns a thread to fetch, writes into {@link LyricCacheManager} and the UI polls {@link LyricGetter#isFinish()}; the fetching is {@link KuGouUtil#getLyric(String)} (/search, then /download?fmt=lrc).
 */
public class KuGouLyricGetter implements LyricGetter {
    private GetLyricThread thread;

    @Override
    public void run(MusicSource musicSource) {
        thread = new GetLyricThread(musicSource);
        thread.start();
    }

    @Override
    public void runAndWait(MusicSource musicSource) {
        thread = new GetLyricThread(musicSource);
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public void stop() {
        if (thread != null)
            thread.interrupt();
    }

    @Override
    public boolean isFinish() {
        return thread != null && !thread.isAlive();
    }

    @Override
    public Lyric getLyric() {
        return thread == null ? null : thread.lyric;
    }

    private static class GetLyricThread extends Thread {
        private final MusicSource source;
        public volatile Lyric lyric;

        public GetLyricThread(MusicSource source) {
            this.source = source;
            setDaemon(true);
            setName("IMP-KuGou-Lyric");
        }

        @Override
        public void run() {
            try {
                lyric = KuGouUtil.getLyric(source.getIdentifier());
            } catch (Throwable ignored) {
                lyric = Lyric.EMPTY;
            }
            // Cache even when unavailable (null / empty): avoids going online for it again on every playback
            LyricCacheManager.cacheAsync(source.getLoaderType() + "_" + source.getIdentifier(),
                    lyric == null ? Lyric.EMPTY : lyric);
        }
    }
}
