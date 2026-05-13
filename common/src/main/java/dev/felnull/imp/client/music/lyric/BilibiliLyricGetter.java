package dev.felnull.imp.client.music.lyric;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.bilibili.BiliBiliUtil;
import dev.felnull.imp.music.resource.Lyric;
import dev.felnull.imp.music.resource.MusicSource;

public class BilibiliLyricGetter implements LyricGetter{
    private GetLyricThread thread;

    @Override
    public void run(MusicSource musicSource) {
        thread = new GetLyricThread(musicSource);
        thread.start();
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
            try{
                lyric = BiliBiliUtil.fetchLyrics(source.getIdentifier(), IamMusicPlayer.getConfig().bilibiliConfig.bilibiliCookie);
            }catch (Exception ignore){

            }
        }
    }
}
