package dev.felnull.imp.client.music.loader;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.bilibili.BiliBiliSponsorSegments;
import dev.felnull.imp.client.bilibili.BiliBiliUtil;
import dev.felnull.imp.client.cache.AudioCacheManager;
import dev.felnull.imp.client.lava.LavaPlayerManager;
import dev.felnull.imp.client.music.MusicEngine;
import dev.felnull.imp.client.music.MusicLoadFailure;
import dev.felnull.imp.client.music.TrialClip;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.client.music.player.LavaMusicPlayer;
import dev.felnull.imp.client.music.player.MusicPlayer;
import dev.felnull.imp.client.music.player.MusicSkipProvider;
import dev.felnull.imp.music.resource.MusicSource;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

public class LavaMusicLoader implements MusicLoader {
    private MusicSource musicSource;
    private AudioTrack audioTrack;
    private boolean ringLoad;
    /** The segments the read side skips for this track (Bilibili sponsor/ad); {@link MusicSkipProvider#NONE} = skip nothing. */
    private MusicSkipProvider skipProvider = MusicSkipProvider.NONE;

    @Override
    public void setRingLoad(boolean ringLoad) {
        this.ringLoad = ringLoad;
    }

    @Override
    public @NotNull MusicPlayer<?, ?> createMusicPlayer(UUID musicPlayerId) {
        var player = new LavaMusicPlayer(musicPlayerId, audioTrack, musicSource);
        player.setSkipProvider(skipProvider);
        return player;
    }

    @Override
    public void tryLoad(@NotNull MusicSource source) throws Exception {
        if (!isSupportMedia(source))
            throw new RuntimeException("Unsupported media");

        var lm = LavaPlayerManager.getInstance();

        var wr = wrappedIdentifier(source);
        if (wr == null)
            throw new RuntimeException("Failed to get wrapped identifier");

        var track = lm.loadTrack(wr);
        if (track.isEmpty())
            throw new RuntimeException("Failed to load track");

        if (source.isLive() != track.get().getInfo().isStream)
            throw new RuntimeException("Discrepancies in live information");

        this.audioTrack = track.get();
        this.musicSource = source;

        // [Trial fragment] A paid track handed to an account that may not play it comes back as a ~30 s clip.
        // Caching that would keep the fragment forever, even after the account/cookie is fixed.
        var fragmentMs = trialFragmentMs(source, track.get());
        // The decoded length is "unknown" for a **remote** track at this point (measured: the same clip reads 30066 ms
        // from the audio cache and nothing straight from the network), so on the fresh path the platform's own answer decides.
        var trialAddress = fragmentMs <= 0 && TrialClip.isTrialAddress(source);
        if (trialAddress)
            fragmentMs = Math.max(0, TrialClip.clipMs(source));

        if (fragmentMs > 0 || trialAddress) {
            reportTrialFragment(source, fragmentMs, false, trialAddress);
            // In world playback a trial clip is worse than nothing: the boombox would sit on it for the clip's 30 s
            // (or, worse, the declared length). Failing the load uses the ring's own drop-this-track fallback.
            if (refusesTrialInRing(source, true, ringLoad))
                throw new RuntimeException("Trial fragment skipped in world playback");
        } else {
            MusicLoadFailure.clearNotice(source.getIdentifier());
        }

        // Cache under the loader's own identifier (`bilibili:BV…`): the reader looks that key up, while
        // `track.getIdentifier()` is the expiring CDN URL, so every play used to miss its own entry.
        // A trial clip is never cached — that is how the poisoned entries got in in the first place.
        if (!source.isLive() && fragmentMs <= 0 && !trialAddress)
            AudioCacheManager.cacheAsync(wr, track.get().getInfo().uri, cacheHeaders(source, track.get().getInfo().uri));

        // Bilibili sponsor/ad segments: the lookup runs on a background thread (about two seconds the first time), and a
        // late answer still skips from the current read head. The boombox path comes through here too (the server's
        // timeline also runs on content, see MusicRing).
        this.skipProvider = BiliBiliSponsorSegments.providerFor(source);
    }

    /**
     * "This is a trial fragment": the audio that came back is a fraction of the declared length — deliberately narrow (over a minute, clip ≤ 61 s while the song claims ≥ 30 s more), and shared with {@link CacheMusicLoader}.
     */
    static long trialFragmentMs(MusicSource source, AudioTrack track) {
        long want = source.getDuration();
        long got = track.getDuration();
        if (want < 60_000 || got <= 0 || got > 61_000 || want - got < 30_000)
            return 0;
        return got;
    }

    /** Records "it plays, but only a clip": the notice plus the unconditional warning line. {@code cached} = it came from the audio cache. */
    static void reportTrialFragment(MusicSource source, long fragmentMs, boolean cached, boolean trialAddress) {
        String what = fragmentMs > 0
                ? MusicLoadFailure.text("imp.reason.trial.clip", fragmentMs / 1000, source.getDuration() / 1000)
                : MusicLoadFailure.text("imp.reason.trial.only", source.getDuration() / 1000);
        MusicLoadFailure.noteNotice(source.getIdentifier(), what);
        MusicEngine.getInstance().getLogger().warn(
                "Music load warning | trial fragment | loaderType={} | identifier={} | gotMs={} | wantMs={} | cached={} | byAddress={}",
                source.getLoaderType(), LinkDebug.safeIdentifier(source.getIdentifier()), fragmentMs, source.getDuration(), cached, trialAddress);
    }

    /** True when world playback must refuse this clip — the ring then drops the track and moves on. */
    static boolean refusesTrialInRing(MusicSource source, boolean trial, boolean ringLoad) {
        if (!trial || !ringLoad || !IamMusicPlayer.getConfig().boomboxConfig.skipTrialFragment)
            return false;
        MusicLoadFailure.noteReason(source.getIdentifier(), MusicLoadFailure.text("imp.reason.trial.skipped"));
        return true;
    }

    /** The CDN wants the same browser identity the stream sends; the other media take no headers. */
    private static Map<String, String> cacheHeaders(MusicSource source, String url) {
        return IMPMusicMedias.BILIBILI.getName().equals(source.getLoaderType()) ? BiliBiliUtil.cdnHeaders(url) : null;
    }

    protected boolean isSupportMedia(MusicSource source) {
        if (source.isLive() && source.getLoaderType().isEmpty())
            return true;

        var lm = LavaPlayerManager.getInstance();
        return lm.getMedias().containsKey(source.getLoaderType());
    }

    protected String wrappedIdentifier(MusicSource source) throws Exception {
        return source.getIdentifier();
    }

    @Override
    public int priority() {
        return 0;
    }
}
