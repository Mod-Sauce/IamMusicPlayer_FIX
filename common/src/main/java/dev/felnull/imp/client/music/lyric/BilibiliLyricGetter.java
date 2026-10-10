package dev.felnull.imp.client.music.lyric;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.bilibili.BiliBiliArcTags;
import dev.felnull.imp.client.bilibili.BiliBiliUtil;
import dev.felnull.imp.client.cache.LyricCacheManager;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.music.resource.Lyric;
import dev.felnull.imp.music.resource.MusicSource;

public class BilibiliLyricGetter implements LyricGetter{

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger(BilibiliLyricGetter.class);
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

    /** The local trace of a UP-space import is the zone/season tag: a favourites import and a refresh write only the upload date, and a hand-added BV that was played once has neither. */
    private static boolean isUpImported(String bvid) {
        var tags = BiliBiliArcTags.getInstance();
        return !tags.zoneOf(bvid).isBlank() || !tags.seasonsOf(bvid).isEmpty();
    }

    private static boolean legacySwitchChecked;

    /** Called once at client init: moves the old keys over before Cloth's first save (which rewrites the whole config file under the new field names). */
    public static void migrateLegacySwitch() {
        subtitlesAsLyrics();
    }

    /**
     * Fallback for the renamed master switch ({@code subtitleEnabled} → {@code subtitleAsLyrics}): an old config that
     * still carries the old key has it moved and rewritten to the new name, once, on the first read.
     */
    private static boolean subtitlesAsLyrics() {
        var cfg = IamMusicPlayer.getConfig().bilibiliConfig;
        if (legacySwitchChecked)
            return cfg.subtitleAsLyrics;
        legacySwitchChecked = true;
        try {
            var path = dev.architectury.platform.Platform.getConfigFolder().resolve("iammusicplayer.toml");
            if (!java.nio.file.Files.exists(path))
                return cfg.subtitleAsLyrics;
            var text = java.nio.file.Files.readString(path);
            if (text.contains("subtitleAsLyrics"))
                return cfg.subtitleAsLyrics;
            var m = java.util.regex.Pattern.compile("(?m)^\\s*subtitleEnabled\\s*=\\s*(true|false)").matcher(text);
            if (!m.find())
                return cfg.subtitleAsLyrics;
            cfg.subtitleAsLyrics = Boolean.parseBoolean(m.group(1));
            java.nio.file.Files.writeString(path, text.replace("subtitleEnabled", "subtitleAsLyrics"));
            LOGGER.info("BiliBili subtitle master switch migrated | subtitleEnabled={} -> subtitleAsLyrics", m.group(1));
        } catch (Throwable t) {
            LOGGER.warn("BiliBili subtitle master switch migration skipped", t);
        }
        return cfg.subtitleAsLyrics;
    }

    private static class GetLyricThread extends Thread{
        private final MusicSource source;
        public volatile Lyric lyric;

        public GetLyricThread(MusicSource source){
            this.source = source;
        }

        @Override
        public void run() {
            try{
                // The lookup must use the **bare video number**: the tag cache is keyed by bvid, while an expanded part's
                // identifier carries the part suffix (`BV…-p2`).
                var bvid = BiliBiliUtil.stripPage(source.getIdentifier());
                if (!subtitlesAsLyrics()) {
                    // Every early return needs a trace, otherwise "no subtitles" and "the request failed" look the same in the log.
                    LinkDebug.log("BiliBili lyric skip", "id", source.getIdentifier(), "reason", "subtitleAsLyrics=false");
                    lyric = Lyric.EMPTY;
                    return;
                }
                if (IamMusicPlayer.getConfig().bilibiliConfig.subtitleUpOnly && !isUpImported(bvid)) {
                    if (LinkDebug.shouldLogThrottled("bililyric:uponly:" + source.getIdentifier()))
                        LinkDebug.log("BiliBili lyric skip", "id", source.getIdentifier(),
                                "reason", "subtitleUpOnly 且这条不是 UP 主页导入的（没有分区/合集标签）");
                    lyric = Lyric.EMPTY;
                    return;
                }
                // The cookie must be the **normalized** one: users paste a phone client's JSON export into the config
                // field, and sending that JSON blob as a Cookie header would not authenticate anything.
                lyric = BiliBiliUtil.fetchLyrics(source.getIdentifier(), BiliBiliUtil.spaceCookie());
            }catch (Exception e){
                if (LinkDebug.shouldLogThrottled("bililyric:err:" + source.getIdentifier()))
                    LinkDebug.log("BiliBili lyric failed", "id", source.getIdentifier(),
                            "ex", e.getClass().getSimpleName(), "msg", String.valueOf(e.getMessage()));
            }
            LyricCacheManager.cacheAsync(source.getLoaderType() + "_" + source.getIdentifier(), lyric);
        }
    }
}
