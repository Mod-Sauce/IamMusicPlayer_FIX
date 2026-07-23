package dev.felnull.imp.client.music.media;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.sourceManager.QQMusicAudioSourceManager;
import dev.felnull.imp.client.qqMusic.QQMusicUtil;

public class QQMusicMedia extends LavaPlayerBaseMusicMedia{
    public QQMusicMedia(String name) {
        super(name);
    }

    @Override
    public void registerSourceManager(AudioPlayerManager audioPlayerManager) {
        audioPlayerManager.registerSourceManager(new QQMusicAudioSourceManager());
    }

    @Override
    public boolean match(AudioTrack track) {
        return track.getSourceManager() instanceof QQMusicAudioSourceManager;
    }

    @Override
    public MusicMediaResult load(String sourceName) throws Exception {
        if(!IamMusicPlayer.getConfig().QQMusicConfig.enableQQMusic)return null;
        return QQMusicUtil.getMusicMediaResult(sourceName);
    }

    @Override
    public boolean isSearchable() {
        return false;
    }
}
