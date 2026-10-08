package dev.felnull.imp.client.music.lyric;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.cache.LyricCacheManager;
import dev.felnull.imp.music.resource.Lyric;
import dev.felnull.imp.music.resource.MusicSource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class IMPLyricGetter {
    private static final Map<String, LyricGetterType> ALL_GETTER = new HashMap<>();
    /** Fetches started at music-load time (see {@link #prefetch}), kept so the renderers pick up the same result. */
    private static final Map<String, LyricGetter> PREFETCHED = new LinkedHashMap<>();
    /** Small cap: one entry can hold a whole video's transcript. */
    private static final int PREFETCH_KEEP = 16;

    public static void register(LyricGetterType getterType){
        ALL_GETTER.put(getterType.getLoaderType(), getterType);
    }

    /**
     * Starts the fetch while the audio is still loading: the renderers only ask once they have something to draw, so on
     * a cache miss the first line would have no text.
     */
    public static void prefetch(MusicSource source) {
        if (source == null) return;
        var cfg = IamMusicPlayer.getConfig();
        // With every lyric surface off nothing would ever show it, and the renderers start no fetch either.
        // ⚠️ The handheld bottom bar's line is a surface of its own (`hudBottomLyricLine`) — leaving it out of this
        // list meant "bottom bar on, the other three off" prefetched nothing and the first line arrived late again.
        if (!cfg.showBlockLyrics && !cfg.enableHudLyric && !cfg.enableMusicInfoHUD && !cfg.hudBottomLyricLine) return;
        var type = ALL_GETTER.get(source.getLoaderType());
        if (type == null) return;
        var cacheID = source.getLoaderType() + "_" + source.getIdentifier();
        if (LyricCacheManager.has(cacheID)) return;
        synchronized (PREFETCHED) {
            var running = PREFETCHED.get(cacheID);
            if (running != null) {
                var lyric = running.isFinish() ? running.getLyric() : null;
                // A finished fetch that came back empty must not stick: the disk cache stores neither, so the next load retries.
                if (lyric == null || !lyric.isEmpty()) return;
                PREFETCHED.remove(cacheID);
            }
            if (PREFETCHED.size() >= PREFETCH_KEEP) {
                var oldest = PREFETCHED.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
            var getter = type.getLyricGetter();
            getter.run(source);
            PREFETCHED.put(cacheID, getter);
        }
    }

    public static LyricGetter getGetter(MusicSource source){
        if(!ALL_GETTER.containsKey(source.getLoaderType()))return null;
        var cacheID = source.getLoaderType() + "_" + source.getIdentifier();
        var prefetched = prefetchedView(cacheID);
        if (prefetched != null) return prefetched;
        if(LyricCacheManager.has(cacheID))
            return cachedView(cacheID);
        return ALL_GETTER.get(source.getLoaderType()).getLyricGetter();
    }

    /** The getter of a running or finished prefetch, wrapped so a renderer's {@code stop()} cannot kill the shared fetch. */
    private static LyricGetter prefetchedView(String cacheID) {
        LyricGetter getter;
        synchronized (PREFETCHED) {
            getter = PREFETCHED.get(cacheID);
        }
        if (getter == null) return null;
        return new LyricGetter() {
            @Override
            public void run(MusicSource musicSource) {
            }

            @Override
            public void runAndWait(MusicSource musicSource) {
            }

            @Override
            public void stop() {
            }

            @Override
            public boolean isFinish() {
                return getter.isFinish();
            }

            @Override
            public Lyric getLyric() {
                return getter.getLyric();
            }
        };
    }

    private static LyricGetter cachedView(String cacheID) {
        return new LyricGetter() {
            @Override
            public void run(MusicSource musicSource) {
            }

            @Override
            public void runAndWait(MusicSource musicSource) {
            }

            @Override
            public void stop() {
            }

            @Override
            public boolean isFinish() {
                return true;
            }

            @Override
            public Lyric getLyric() {
                return LyricCacheManager.get(cacheID);
            }
        };
    }

    public static LyricGetter getGetter(String type){
        var g = ALL_GETTER.get(type);
        if(g == null)return null;
        return g.getLyricGetter();
    }

    public static void init(){
        register(new LyricGetterType() {
            @Override
            public String getLoaderType() {
                return "netease";
            }

            @Override
            public LyricGetter getLyricGetter() {
                return new NetEaseLyricGetter();
            }
        });

        register(new LyricGetterType() {
            @Override
            public String getLoaderType() {
                return "bilibili";
            }

            @Override
            public LyricGetter getLyricGetter() {
                return new BilibiliLyricGetter();
            }
        });

        register(new LyricGetterType() {
            @Override
            public String getLoaderType() {
                return "qq_music";
            }

            @Override
            public LyricGetter getLyricGetter() {
                return new QQMusicLyricGetter();
            }
        });

        register(new LyricGetterType() {
            @Override
            public String getLoaderType() {
                return "kugou";
            }

            @Override
            public LyricGetter getLyricGetter() {
                return new KuGouLyricGetter();
            }
        });
    }
}
