package dev.felnull.imp.client.music.loader;

import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.felnull.imp.client.cache.AudioCacheManager;
import dev.felnull.imp.client.bilibili.BiliBiliSponsorSegments;
import dev.felnull.imp.client.lava.LavaPlayerManager;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.client.music.player.LavaMusicPlayer;
import dev.felnull.imp.client.music.player.MusicPlayer;
import dev.felnull.imp.client.music.player.MusicSkipProvider;
import dev.felnull.imp.music.resource.MusicSource;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public class CacheMusicLoader implements MusicLoader{
    private MusicSource musicSource;
    private AudioTrack audioTrack;
    private boolean ringLoad;
    /** 本曲读取端要跳的片段（B站 赞助/广告）；{@link MusicSkipProvider#NONE} = 不跳。 */
    private MusicSkipProvider skipProvider = MusicSkipProvider.NONE;

    @Override
    public void setRingLoad(boolean ringLoad) {
        this.ringLoad = ringLoad;
    }

    private static final LocalAudioSourceManager MANAGER = new LocalAudioSourceManager();
    @Override
    public @NotNull MusicPlayer<?, ?> createMusicPlayer(UUID musicPlayerId) {
        var player = new LavaMusicPlayer(musicPlayerId, audioTrack, musicSource);
        player.setSkipProvider(skipProvider);
        return player;
    }

    @Override
    public void tryLoad(@NotNull MusicSource source) {
        var id = cacheKey(source);
        if(!AudioCacheManager.has(id))
            throw new RuntimeException();
        var file = AudioCacheManager.getPath(id);
        if(file == null)
            throw new RuntimeException();
        LinkDebug.log("AudioCache hit", "id", id, "file", file.getFileName());

        var result = MANAGER.loadItem(LavaPlayerManager.getInstance().getAudioPlayerManager(), new AudioReference(file.toString(),
                ""));
        if(!(result instanceof AudioTrack track))
            throw new RuntimeException();
        audioTrack = track;
        musicSource = source;

        // This loader runs **before** LavaMusicLoader, so a poisoned entry (a 30 s clip cached before we stopped
        // caching them) would otherwise slip past the trial-fragment rules entirely and play in world playback.
        var fragmentMs = LavaMusicLoader.trialFragmentMs(source, track);
        if (fragmentMs > 0) {
            LavaMusicLoader.reportTrialFragment(source, fragmentMs, true, false);
            AudioCacheManager.drop(id);     // self-heal: the next attempt fetches it again (and may get the full track)
            if (LavaMusicLoader.refusesTrialInRing(source, true, ringLoad))
                throw new RuntimeException("Trial fragment skipped in world playback (cached copy)");
        }

        // 缓存文件里连广告一起存着，所以缓存命中的这条路也要跳（同一个播放器读取端）。
        this.skipProvider = BiliBiliSponsorSegments.providerFor(source);
    }

    /** The loaders cache under their own wrapped LavaPlayer identifier, not under the bare source id. */
    private static String cacheKey(MusicSource source) {
        var type = source.getLoaderType();
        if (Objects.equals(type, IMPMusicMedias.NETEASE_MUSIC.getName())) return "netease:" + source.getIdentifier();
        if (Objects.equals(type, IMPMusicMedias.BILIBILI.getName())) return "bilibili:" + source.getIdentifier();
        if (Objects.equals(type, IMPMusicMedias.KUGOU.getName())) return "kugou:" + source.getIdentifier();
        if (Objects.equals(type, IMPMusicMedias.QQ_MUSIC.getName())) return "qq_music:" + source.getIdentifier();
        return source.getIdentifier();
    }

    @Override
    public int priority() {
        return 114514;
    }
}
