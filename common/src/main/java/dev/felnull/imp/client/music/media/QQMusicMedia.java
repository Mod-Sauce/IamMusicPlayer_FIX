package dev.felnull.imp.client.music.media;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.link.MusicLinks;
import dev.felnull.imp.client.music.sourceManager.QQMusicAudioSourceManager;
import dev.felnull.imp.client.qqMusic.QQMusicUtil;

import java.util.List;

public class QQMusicMedia extends LavaPlayerBaseMusicMedia{
    public QQMusicMedia(String name) {
        super(name);
        // Register QQ Music's identifier conversion: the songmid in a share link → numeric songid (the input box and the playback/lyrics
        // channels need the numeric id). ⚠️ The conversion needs the network, so it must go through MusicLinks' injectable hook — the
        // resolver itself uses no network and depends on no client class.
        MusicLinks.setIdentifierConverter((platform, identifier) -> {
            if (!"qq_music".equals(platform) || identifier == null || identifier.isEmpty())
                return null;
            // already all digits = it is a songid to begin with, no conversion needed
            if (identifier.chars().allMatch(Character::isDigit))
                return null;
            var songId = QQMusicUtil.getSongIdFromMid(identifier);
            return songId == null ? null : String.valueOf(songId);
        });
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
        return true;
    }

    @Override
    public List<MusicMediaResult> search(String searchText) {
        return QQMusicUtil.searchMusicResult(searchText);
    }
}
