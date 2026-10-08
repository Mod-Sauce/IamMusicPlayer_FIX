package dev.felnull.imp.client.music;

import dev.felnull.imp.client.music.loader.IMPMusicLoaders;
import dev.felnull.imp.client.music.loader.MusicLoader;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.client.music.player.MusicLoadChunk;
import dev.felnull.imp.client.music.player.MusicPlayer;
import dev.felnull.imp.client.music.speaker.MusicSpeaker;
import dev.felnull.imp.client.music.task.MusicDestroyRunner;
import dev.felnull.imp.client.music.task.MusicLoaderDestroyRunner;
import dev.felnull.imp.music.resource.MusicSource;
import dev.felnull.imp.music.tracker.MusicTracker;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class MusicEntry {
    private final Map<UUID, MusicTracker> reserveTrackers = new HashMap<>();
    private final AtomicReference<MusicPlayer<?, ?>> musicPlayer = new AtomicReference<>();
    private final MusicSource source;
    private final long startPosition;
    private final UUID musicPlayerId;
    private boolean loaded;
    private boolean stopped;
    private boolean loadFailed;
    /** World playback (boombox / cassette) instead of the playlist page — see {@link #isRingLoad()}. */
    private boolean ringLoad;

    public float getCurrentPositionProgress() {
        if (source.isLive())
            return 0;
        // 位置与时长都是**视频时间**（跳段之后位置会跟着跳），所以这里直接除即可。
        return (float) getCurrentPosition() / (float) source.getDuration();
    }

    protected MusicPlayer<?, ?> getMusicPlayer() {
        return musicPlayer.get();
    }

    protected int getTaskCount() {
        if (musicPlayer.get() != null)
            return musicPlayer.get().getTaskCount();
        return 0;
    }

    protected MusicEntry(UUID musicPlayerId, MusicSource source, long position) {
        this(musicPlayerId, source, position, false);
    }

    protected MusicEntry(UUID musicPlayerId, MusicSource source, long position, boolean ringLoad) {
        this.musicPlayerId = musicPlayerId;
        this.source = source;
        this.startPosition = position;
        this.ringLoad = ringLoad;
    }

    /** True when this entry plays through a world ringer (boombox / cassette) rather than the playlist page. */
    public boolean isRingLoad() {
        return ringLoad;
    }

    public MusicSource getSource() {
        return source;
    }

    public long getStartPosition() {
        return startPosition;
    }

    public List<MusicLoadChunk> getLoadChunks() {
        if (musicPlayer.get() != null)
            return musicPlayer.get().getLoadChunks();

        return new ArrayList<>();
    }

    public int getChannels() {
        if (musicPlayer.get() != null)
            return musicPlayer.get().getAudioInfo().channel();
        return 0;
    }

    public long getCurrentPosition() {
        if (musicPlayer.get() != null) {
            long pos = musicPlayer.get().getPosition();
            if (pos >= 0) return pos;
        }
        return startPosition;
    }

    public float getCurrentAudioWave(int channel) {
        if (musicPlayer.get() != null)
            return musicPlayer.get().getCurrentAudioWave(channel);
        return 0;
    }

    protected void playStart(long delay) {
        if (LinkDebug.isEnabled())
            LinkDebug.log("MusicEntry.playStart", "loaderType", source.getLoaderType(),
                    "id", source.getIdentifier(), "delay", delay,
                    "entryLoaded", loaded, "playerFields", playerFields());
        musicPlayer.get().play(delay);
    }

    protected boolean tick() {
        if (loadFailed) {
            if (LinkDebug.isEnabled())
                LinkDebug.log("MusicEntry.tick->destroy", "reason", "loadFailed=true",
                        "loaderType", source.getLoaderType(), "id", source.getIdentifier(),
                        "playerFields", playerFields());
            destroy();
            return false;
        }

        if (musicPlayer.get() != null) {
            if (musicPlayer.get().waitDestroy()) {
                if (LinkDebug.isEnabled())
                    LinkDebug.log("MusicEntry.tick->destroy", "reason", "waitDestroy=true",
                            "loaderType", source.getLoaderType(), "id", source.getIdentifier(),
                            "playerFields", playerFields());
                destroy();
                return false;
            }

            if (musicPlayer.get().isDestroy())
                return false;

            musicPlayer.get().tick();
        }

        return true;
    }

    /**
     * [Diagnostic log only, read-only] Reads the player's private fields ({@code waitDestroy() = finished || position >= duration}), so the raw fields tell which fired; returns a placeholder when reflection fails.
     */
    private String playerFields() {
        var p = musicPlayer.get();
        if (p == null)
            return "player=null";
        var sb = new StringBuilder();
        for (var f : new String[]{"loaded", "playing", "finished", "loadEnd", "reading",
                "pause", "destroy", "totalReadTime", "startTime", "startPosition"}) {
            try {
                var fd = findField(p.getClass(), f);
                if (fd == null) {
                    sb.append(f).append("=? ");
                    continue;
                }
                fd.setAccessible(true);
                sb.append(f).append('=').append(fd.get(p)).append(' ');
            } catch (Throwable t) {
                sb.append(f).append("=! ");
            }
        }
        try {
            var m = p.getClass().getMethod("getLoadChunks");
            var chunks = m.invoke(p);
            sb.append("chunks=").append(chunks instanceof java.util.Collection<?> c ? c.size() : "?");
        } catch (Throwable t) {
            sb.append("chunks=!");
        }
        return sb.toString();
    }

    private static java.lang.reflect.Field findField(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                return k.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    protected void pause() {
        if (musicPlayer.get() != null) {
            musicPlayer.get().pause();
        }
    }

    protected void resume() {
        if (musicPlayer.get() != null) {
            musicPlayer.get().resume();
        }
    }

    protected void destroy() {
        stopped = true;

        // [Diagnostic log, only when LinkDebug is on] Prints the caller and the three states (from = the first **non-JDK** frame in that thread's stack).
        if (LinkDebug.isEnabled()) {
            var p = musicPlayer.get();
            LinkDebug.log("MusicEntry.destroy", "loaderType", source.getLoaderType(),
                    "id", source.getIdentifier(), "stopped->", true,
                    "player", p == null ? "null" : p.getClass().getSimpleName(),
                    "playing", p != null && p.isPlaying(), "isLoaded", p != null && p.isLoaded(),
                    "entryLoaded", loaded,
                    "playerFields", playerFields(),
                    "from", callerFrame());
        }

        if (musicPlayer.get() != null)
            musicPlayer.get().destroyNonThrow();
    }

    /** The first non-JDK / non-this-class frame in the current thread's stack. */
    private static String callerFrame() {
        try {
            var st = Thread.currentThread().getStackTrace();
            for (var e : st) {
                var cn = e.getClassName();
                if (cn.startsWith("java.") || cn.startsWith("jdk.") || cn.startsWith("sun.")
                        || cn.equals(MusicEntry.class.getName()))
                    continue;
                return cn + "#" + e.getMethodName() + ":" + e.getLineNumber();
            }
            return "(no non-jdk frame)";
        } catch (Throwable t) {
            return "(stack unavailable)";
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public boolean isPlaying() {
        if (musicPlayer.get() != null)
            return musicPlayer.get().isPlaying();
        return false;
    }

    protected int getSpeakerCount() {
        if (musicPlayer.get() != null) return musicPlayer.get().getSpeakerCount();
        return 0;
    }

    protected boolean updateMusicTracker(UUID speakerId, MusicTracker tracker) {
        var player = musicPlayer.get();
        if (player == null) {
            synchronized (reserveTrackers) {
                return reserveTrackers.put(speakerId, tracker) != null;
            }
        }
        var sp = player.getSpeaker(speakerId);
        if (sp != null) {
            sp.setTracker(tracker);
            return true;
        }
        return false;
    }

    protected boolean removeSpeaker(UUID speakerId) {
        var player = musicPlayer.get();

        if (player == null) {
            synchronized (reserveTrackers) {
                return reserveTrackers.remove(speakerId) != null;
            }
        }
        if (!player.existSpeaker(speakerId)) return false;

        player.removeSpeaker(speakerId);
        return true;
    }

    protected boolean addSpeaker(UUID speakerId, MusicTracker tracker) {
        if (musicPlayer.get() == null) {
            synchronized (reserveTrackers) {
                if (reserveTrackers.containsKey(speakerId)) return false;

                reserveTrackers.put(speakerId, tracker);
            }
            return true;
        }

        var spk = musicPlayer.get().getSpeaker(speakerId);
        if (spk != null) return false;

        musicPlayer.get().addSpeaker(speakerId, new MusicSpeaker(musicPlayerId, speakerId, tracker));
        return true;
    }

    private MusicDestroyRunner createDestroyRunner() {
        var me = MusicEngine.getInstance();
        return new MusicLoaderDestroyRunner(me.getMusicDestroyRunner(), () -> stopped);
    }

    /**
     * Appends a concrete reason to generic failures such as "no available loaders" (empty when there is none); each platform records it via {@link MusicLoadFailure}.
     */
    private static String reasonSuffix(@org.jetbrains.annotations.Nullable String identifier) {
        var r = dev.felnull.imp.client.music.MusicLoadFailure.reason(identifier);
        return r == null || r.isBlank() ? "" : " - " + r;
    }

    protected void loadStart(LoadCompleteListener listener) {
        final long startTime = System.currentTimeMillis();
        var me = MusicEngine.getInstance();
        var runner = createDestroyRunner();

        var cf = CompletableFuture.supplyAsync(() -> {
            var loader = selectLoader(source);
            if (loader == null)
                // **Append the platform's concrete reason** to the generic sentence (e.g. kugou's "no copyright" /
                // "payment required"): the concrete reason is recorded into MusicLoadFailure by each platform at the failure point.
                throw new RuntimeException("No available loaders found" + reasonSuffix(source.getIdentifier()));
            return loader;
        }, me.getMusicAsyncExecutor()).thenApplyAsync(ret -> {
            return ret.createMusicPlayer(musicPlayerId);
        }, me.getMusicTickExecutor()).thenApplyAsync(ret -> {
            musicPlayer.set(ret);

            runner.run(ret::destroyNonThrow);
            return ret;
        }, me.getMusicAsyncExecutor()).thenApplyAsync(ret -> {
            synchronized (reserveTrackers) {
                reserveTrackers.forEach(this::addSpeaker);
                reserveTrackers.clear();
            }
            runner.run(ret::destroyNonThrow);
            return ret;
        }, me.getMusicTickExecutor()).thenApplyAsync(ret -> {
            return new MusicLoadStartResult(ret, ret.loadStart(startPosition));
        }, me.getMusicTickExecutor()).thenApplyAsync(ret -> {
            try {
                var aret = ret.loadAsync();
                runner.run(() -> ret.musicPlayer().destroyNonThrow());
                return new MusicLoadAsyncResult(new LoadResult(true, null), aret, ret.musicPlayer());
            } catch (Exception e) {
                return new MusicLoadAsyncResult(new LoadResult(false, e), null, null);
            }
        }, me.getMusicAsyncExecutor()).thenApplyAsync(ret -> {
            runner.run(() -> {
                if (ret.musicPlayer() != null) ret.musicPlayer().destroyNonThrow();
            });

            if (ret.musicLoadEndResult() != null) {
                ret.musicLoadEndResult().apply();
                runner.run(() -> ret.musicLoadEndResult().musicPlayer().destroyNonThrow());
            }
            return ret.loadResult();
        }, me.getMusicTickExecutor());

        cf.whenCompleteAsync((ret, throwable) -> {
            loaded = ret != null && ret.success;

            // [Hint & auto stop] Record whether this source can actually load: success / normal cancel clears the record, a real failure sets one.
            // The boombox UI needs it to show the hint — otherwise there is no hint, no sound, and the time bar still runs.
            if (loaded || isCancelled(throwable))
                dev.felnull.imp.client.music.MusicLoadFailure.clear(source.getIdentifier());
            else
                dev.felnull.imp.client.music.MusicLoadFailure.report(source.getLoaderType(), source.getIdentifier());

            if (LinkDebug.isEnabled())
                LinkDebug.log("MusicEntry.loadComplete", "loaderType", source.getLoaderType(),
                        "id", source.getIdentifier(), "ret", ret == null ? "null" : "success=" + ret.success,
                        "throwable", throwable == null ? "none"
                                : throwable.getClass().getSimpleName() + ": " + throwable.getMessage(),
                        "playerFields", playerFields());

            if (throwable != null) {
                loadFailed = true;

                // A load failure must be logged unconditionally with its source and identifier: the loaders throw plain RuntimeExceptions that would otherwise be silent in the log.
                // A cancel (MusicLoadCancelled = "this slot was already stopped") also reaches here and is an ordinary INFO, not a fault.
                var message = String.valueOf(throwable.getMessage());
                // The identifier string may be **a whole URL** (for the direct-link / upload channels), possibly carrying a signature or token ⇒ mask it before logging.
                var safeId = LinkDebug.safeIdentifier(source.getIdentifier());
                var failReason = dev.felnull.imp.client.music.MusicLoadFailure.reason(source.getIdentifier());
                if (isCancelled(throwable))
                    MusicEngine.getInstance().getLogger().info(
                            "Music load cancelled (slot stopped) | loaderType={} | identifier={} | {}",
                            source.getLoaderType(), safeId, message);
                else
                    MusicEngine.getInstance().getLogger().error(
                            "Music load error | loaderType={} | identifier={} | reason={} | ex={}: {}",
                            source.getLoaderType(), safeId,
                            failReason == null ? "(none)" : failReason,
                            throwable.getClass().getName(), message,
                            throwable);

                listener.onComplete(false, 0, throwable, musicPlayer.get() != null);
                return;
            }

            if (ret != null)
                listener.onComplete(ret.success, System.currentTimeMillis() - startTime, ret.error, musicPlayer.get() != null);
        }, me.getMusicTickExecutor());
    }

    private MusicLoader selectLoader(MusicSource source) {
        List<MusicLoader> loaders = IMPMusicLoaders.getAllLoader().stream().map(Supplier::get).sorted(Comparator.comparingInt(MusicLoader::priority).reversed()).toList();
        for (MusicLoader loader : loaders) {
            try {
                loader.setRingLoad(ringLoad);
                loader.tryLoad(source);
                return loader;
            } catch (Exception ignored) {
            }
        }
        return null;
    }


    private static record LoadResult(boolean success, Throwable error) {
    }

    private static record MusicLoadStartResult<T, E>(MusicPlayer<T, E> musicPlayer, T input) {
        private MusicLoadEndResult<E> loadAsync() throws Exception {
            return new MusicLoadEndResult<>(musicPlayer, musicPlayer.loadAsync(input));
        }
    }

    private static record MusicLoadEndResult<T>(MusicPlayer<?, T> musicPlayer, T result) {
        private void apply() {
            musicPlayer.loadApply(result);
        }
    }

    private static record MusicLoadAsyncResult(LoadResult loadResult, MusicLoadEndResult<?> musicLoadEndResult,
                                               MusicPlayer<?, ?> musicPlayer) {
    }

    public static record MusicLoadRange(long first, long last) {
    }

    /**
     * "This slot has already been stopped" (cancelled) — thrown by {@code MusicDestroyRunner}.
     */
    public static class MusicLoadCancelled extends RuntimeException {
        public MusicLoadCancelled() {
            super("Stopped!");
        }
    }

    /**
     * Whether this exception is a "cancel".
     */
    private static boolean isCancelled(@Nullable Throwable throwable) {
        for (var t = throwable; t != null; t = t.getCause()) {
            if (t instanceof MusicLoadCancelled)
                return true;
        }
        return false;
    }
}