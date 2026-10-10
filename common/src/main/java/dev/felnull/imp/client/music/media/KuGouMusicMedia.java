package dev.felnull.imp.client.music.media;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.kugou.KuGouUtil;
import dev.felnull.imp.client.music.sourceManager.KuGouAudioSourceManager;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.MusicSource;
import net.minecraft.network.chat.Component;

import java.util.List;

/** KuGou music: identifier = 32-hex hash (case insensitive); data is fetched through {@link KuGouUtil}'s public API.
 * The audio address **expires**, so it does not go into {@link dev.felnull.imp.music.resource.MusicSource} — store the
 * hash, and {@link KuGouAudioSourceManager} resolves it before playback; icon {@code loader_types/kugou.png}. */
public class KuGouMusicMedia extends LavaPlayerBaseMusicMedia {
    protected KuGouMusicMedia(String name) {
        super(name);
    }

    @Override
    public void registerSourceManager(AudioPlayerManager audioPlayerManager) {
        audioPlayerManager.registerSourceManager(new KuGouAudioSourceManager());
    }

    @Override
    public Component getEnterText() {
        // The base class default is the generic "imp.text.enterText.default"; the hint must match the platform.
        return Component.translatable("imp.text.enterText.kugou");
    }

    @Override
    public boolean isSearchable() {
        // In-game search is the main entry point: the KuGou client **has no share link option**
        // (the results give the hash identifier directly).
        return true;
    }

    @Override
    public List<MusicMediaResult> search(String searchText) {
        if (!IamMusicPlayer.getConfig().kugouConfig.enableKugou)
            return List.of();
        // The search API returns no cover, start with an empty image; getSongInfo brings the cover out when the music is actually loaded
        return KuGouUtil.search(searchText, 1, 30).stream()
                .map(s -> new MusicMediaResult(
                        new MusicSource("kugou", s.hash(), s.durationMs()),
                        ImageInfo.EMPTY,
                        s.name() == null ? s.hash() : s.name(),
                        s.author() == null ? "" : s.author()))
                .toList();
    }

    @Override
    public MusicMediaResult load(String sourceName) throws Exception {
        if (!IamMusicPlayer.getConfig().kugouConfig.enableKugou)
            return null;
        var hash = KuGouUtil.normalizeHash(sourceName);
        if (hash == null)
            return null;
        return KuGouUtil.getMusicMediaResult(hash);
    }

    @Override
    public boolean match(AudioTrack track) {
        return track.getSourceManager() instanceof KuGouAudioSourceManager;
    }

    @Override
    public int priority() {
        return 1;
    }
}
