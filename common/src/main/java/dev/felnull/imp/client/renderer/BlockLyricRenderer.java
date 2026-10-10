package dev.felnull.imp.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.api.client.IamMusicPlayerClientAPI;
import dev.felnull.imp.api.client.MusicPlayerAccess;
import dev.felnull.imp.api.client.MusicSpeakerAccess;
import dev.felnull.imp.client.music.MusicEngine;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.client.music.lyric.IMPLyricGetter;
import dev.felnull.imp.client.music.lyric.LyricGetter;
import dev.felnull.imp.client.music.speaker.MusicSpeaker;
import dev.felnull.imp.music.resource.Lyric;
import dev.felnull.imp.music.resource.MusicSource;
import dev.felnull.imp.music.tracker.PlayerMusicTracker;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Floating lyrics in the world: the current line (original light gray, translation pure white, height 12) at {@code scale(-0.025)} with a translucent backdrop, facing the camera.
 */
public class BlockLyricRenderer {
    /** Original text color (light gray); also "not sung yet" in the karaoke path (the bottom HUD reuses it). */
    public static final int ORIGINAL_COLOR = 0xFFAAAAAA;
    /** Translation text color (pure white). */
    private static final int TRANSLATED_COLOR = 0xFFFFFFFF;
    /** Per-character karaoke: the character currently being sung is highlighted in golden yellow (shared with the bottom HUD). */
    public static final int ACTIVE_WORD_COLOR = 0xFFFFD54F;
    /** Per-character karaoke: characters already sung are pure white (shared with the bottom HUD). */
    public static final int SUNG_WORD_COLOR = 0xFFFFFFFF;
    /**
     * **Invisible text color** used to draw the "whole line backdrop" once: black + alpha=4/255 (≈1.6%, invisible to the eye).
     */
    private static final int GHOST_COLOR = 0x04000000;
    /** Float height: lifted further above the tracker position (block center → above the block; player eyes → above the head). */
    private static final double LIFT = 0.8;
    /** Row spacing in the scaled text space (12 px ≈ 0.3 blocks), the same spacing the two-line layout always used. */
    private static final float LINE_SPACING = 12f;
    /** 1 block = 40 px in this space (the pose is scaled by 0.025). */
    private static final float PX_PER_BLOCK = 40f;
    /** Break preference: after these marks the row keeps the original lyric's phrasing (`.` included for English). */
    private static final String SENTENCE_MARKS = "。！？!?;；….";

    private static final Map<UUID, Cache> CACHES = new HashMap<>();

    /**
     * Called by {@code LevelRendererMixin} while rendering the world (the same injection point as "show Ringer range").
     */
    public static void render(PoseStack poseStack, MultiBufferSource multiBufferSource, Camera camera, float partialTick) {
        var client = Minecraft.getInstance();
        if (client.level == null)
            return;

        var players = IamMusicPlayerClientAPI.getInstance().getMusicEngine().getMusicPlayers();
        if (players.isEmpty())
            return;

        // Drop stale caches after an engine reload / leaving the world, to keep UUIDs from piling up
        CACHES.keySet().retainAll(players.keySet());

        for (var entry : players.entrySet()) {
            var player = entry.getValue();
            if (player == null || !player.isLoaded())
                continue;

            var lyric = lyricOf(entry.getKey());
            if (lyric == null || lyric.isEmpty())
                continue;

            // `lyricOffsetSeconds()` is the user's audio-latency compensation (device dependent), see IMPConfig.
            var position = player.getPosition() / 1000f - Lyric.lyricOffsetSeconds();
            var part = lyric.getPart(position);
            var original = part.getA();
            var translated = part.getB();
            if (original == null || original.isBlank())
                continue;
            var words = lyric.getWords(position);

            for (MusicSpeakerAccess speaker : player.getSpeakers().values()) {
                if (speaker == null || speaker.getInfo() == null)
                    continue;
                if (followsPlayer(speaker))
                    continue;   // player-tracked ones are not shown (see the notes on followsPlayer)
                drawAt(poseStack, multiBufferSource, camera, speaker, original, translated,
                        words, position, partialTick, client.font);
            }
        }
    }

    /**
     * Whether the speaker follows the player (manager preview, handheld boombox, earphone-connected boombox) — those are skipped, as text over the head reads as noise; undecidable ⇒ shown.
     */
    private static boolean followsPlayer(MusicSpeakerAccess speaker) {
        return speaker instanceof MusicSpeaker ms && ms.getTracker() instanceof PlayerMusicTracker;
    }

    private static void drawAt(PoseStack poseStack, MultiBufferSource bufferSource, Camera camera,
                              MusicSpeakerAccess speaker, String original, String translated,
                              @Nullable java.util.List<Lyric.Word> words, float position,
                              float partialTick, Font font) {
        var pos = speaker.getInfo().getPosition();
        if (pos == null)
            return;

        var cam = camera.getPosition();
        double dx = pos.x() - cam.x();
        double dy = pos.y() + LIFT - cam.y();
        double dz = pos.z() - cam.z();

        // Too far away: do not draw
        if (dx * dx + dy * dy + dz * dz > 48 * 48)
            return;

        var yaw = camera.getYRot();
        var pitch = camera.getXRot();

        // With a translation, lift everything by half a line so both lines center together
        float yBase = translated != null && !translated.isBlank() ? 0.5f : 0f;

        poseStack.pushPose();
        poseStack.translate(dx, dy, dz);
        poseStack.mulPose(com.mojang.math.Axis.YN.rotationDegrees(yaw));
        poseStack.mulPose(com.mojang.math.Axis.XN.rotationDegrees(-pitch));
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        Matrix4f matrix = poseStack.last().pose();
        int bg = background();

        // [Wrap] Real data does contain very long "lines" (e.g. KuGou krc sometimes merges a whole paragraph into
        // one line: 230 chars ≈ 34 blocks wide). Split at sentence marks first, at a space next, and only cut
        // mid-word when a single word does not fit; extra rows beyond the limit are dropped with "…".
        int maxPx = maxWidthPx();
        var transRows = translated != null && !translated.isBlank()
                ? wrapText(font, translated, maxPx, maxRows()) : java.util.List.<String>of();
        var wordRows = words != null && !words.isEmpty() ? wrapWords(font, words, maxPx, maxRows()) : null;
        var rows = wordRows == null ? wrapText(font, original, maxPx, maxRows()) : null;

        int n = wordRows != null ? wordRows.size() : rows.size();
        int m = transRows.size();
        // Reading order top→bottom, and the translation block sits above the original (unchanged 1×1 layout).
        float yStart = -yBase - (n - 1) * LINE_SPACING / 2f;

        if (wordRows != null) {
            for (int i = 0; i < wordRows.size(); i++)
                drawKaraokeLine(font, matrix, bufferSource, wordRows.get(i), position, yStart + i * LINE_SPACING, bg);
        } else {
            for (int i = 0; i < rows.size(); i++) {
                MutableComponent line = Component.literal(rows.get(i));
                font.drawInBatch(line, -font.width(line) / 2f, yStart + i * LINE_SPACING, ORIGINAL_COLOR, false,
                        matrix, bufferSource, Font.DisplayMode.NORMAL, bg, 0xF000F0);
            }
        }

        for (int j = 0; j < m; j++) {
            MutableComponent trans = Component.literal(transRows.get(j));
            font.drawInBatch(trans, -font.width(trans) / 2f, yStart - (m - j) * LINE_SPACING, TRANSLATED_COLOR, false,
                    matrix, bufferSource, Font.DisplayMode.NORMAL, bg, 0xF000F0);
        }

        poseStack.popPose();
    }

    /**
     * Per-character karaoke: the same centered, unwrapped layout, only the colour differs — sung white, singing golden yellow, not yet sung light gray.
     */
    private static void drawKaraokeLine(Font font, Matrix4f matrix, MultiBufferSource bufferSource,
                                        java.util.List<Lyric.Word> words, float position, float y, int bg) {
        float total = 0f;
        var line = new StringBuilder();
        for (var w : words) {
            total += font.width(w.text());
            line.append(w.text());
        }
        float x0 = -total / 2f;

        // ① Whole-line backdrop: this single pass. Its width matches the total accumulated word by word below,
        //   so the backdrop covers exactly the whole line.
        if (bg != 0) {
            MutableComponent blank = Component.literal(line.toString());
            font.drawInBatch(blank, x0, y, GHOST_COLOR, false,
                    matrix, bufferSource, Font.DisplayMode.NORMAL, bg, 0xF000F0);
        }

        // ② Draw the real characters word by word: bg=0, so each word does not draw its own backdrop again
        //   (adjacent blocks would overlap into black bars)
        float x = x0;
        for (var w : words) {
            var end = w.start() + w.duration();
            int color;
            if (position >= end)
                color = SUNG_WORD_COLOR;
            else if (position >= w.start())
                color = ACTIVE_WORD_COLOR;
            else
                color = ORIGINAL_COLOR;

            MutableComponent c = Component.literal(w.text());
            font.drawInBatch(c, x, y, color, false,
                    matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            x += font.width(c);
        }
    }

    /** Max row width in px, from the config (blocks → px); 6 blocks when the config cannot be read. */
    private static int maxWidthPx() {
        try {
            return (int) (Math.max(2, IamMusicPlayer.getConfig().blockLyricMaxWidth) * PX_PER_BLOCK);
        } catch (Throwable t) {
            return (int) (6 * PX_PER_BLOCK);
        }
    }

    /** Max rows per string, from the config; 4 when the config cannot be read. */
    private static int maxRows() {
        try {
            return Math.max(1, IamMusicPlayer.getConfig().blockLyricMaxLines);
        } catch (Throwable t) {
            return 4;
        }
    }

    private static boolean wrapAtSentences() {
        try {
            return IamMusicPlayer.getConfig().blockLyricWrapSentences;
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * Splits a lyric line into rows no wider than {@code maxPx} (at most {@code maxRows}): sentence mark, then space, then a mid-word cut; leftovers are dropped with "…".
     */
    private static java.util.List<String> wrapText(Font font, String text, int maxPx, int maxRows) {
        var rows = new java.util.ArrayList<String>();
        var rest = text == null ? "" : text.strip();
        if (rest.isEmpty())
            return rows;

        while (!rest.isEmpty()) {
            if (rows.size() + 1 >= maxRows || font.width(rest) <= maxPx) {
                rows.add(fit(font, rest, maxPx));
                break;
            }
            int cut = cutIndex(font, rest, maxPx);
            rows.add(rest.substring(0, cut).strip());
            rest = rest.substring(cut).strip();
        }
        return rows;
    }

    /** The final row: cut to the width limit with "…" when something had to be dropped. */
    private static String fit(Font font, String text, int maxPx) {
        if (font.width(text) <= maxPx)
            return text;
        int cut = cutIndex(font, text, Math.max(1, maxPx - font.width("…")));
        return cut <= 0 ? "…" : text.substring(0, cut).strip() + "…";
    }

    /**
     * Cut index: as far as the width allows, moved back to a sentence mark or space when that keeps at least half the row; never splits a surrogate pair.
     */
    private static int cutIndex(Font font, String text, int maxPx) {
        int width = 0;
        int last = 0;
        int lastSentence = -1;
        int lastSpace = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int cw = font.width(String.valueOf(c));
            if (width + cw > maxPx)
                break;
            width += cw;
            last = i + 1;
            if (wrapAtSentences() && SENTENCE_MARKS.indexOf(c) >= 0)
                lastSentence = i + 1;
            else if (Character.isWhitespace(c))
                lastSpace = i + 1;
        }
        if (last <= 0)
            return 1;
        int minKeep = Math.max(1, last / 2);
        if (lastSentence >= minKeep)
            last = lastSentence;
        else if (lastSpace >= minKeep)
            last = lastSpace;
        if (last > 0 && last < text.length() && Character.isHighSurrogate(text.charAt(last - 1)))
            last--;
        return Math.max(1, last);
    }

    /**
     * Same splitting for per-word (karaoke) data, breaking at word boundaries. Rows beyond the limit are dropped without an ellipsis, since a synthetic word would be highlighted like a real one.
     */
    private static java.util.List<java.util.List<Lyric.Word>> wrapWords(Font font, java.util.List<Lyric.Word> words,
                                                                       int maxPx, int maxRows) {
        var rows = new java.util.ArrayList<java.util.List<Lyric.Word>>();
        var cur = new java.util.ArrayList<Lyric.Word>();
        int width = 0;
        for (var w : words) {
            int ww = font.width(w.text());
            boolean sentenceEnd = !cur.isEmpty() && wrapAtSentences() && endsSentence(cur.get(cur.size() - 1).text());
            if (!cur.isEmpty() && (width + ww > maxPx || (sentenceEnd && width > maxPx * 0.6f))) {
                rows.add(cur);
                if (rows.size() >= maxRows)
                    return rows;
                cur = new java.util.ArrayList<>();
                width = 0;
            }
            cur.add(w);
            width += ww;
        }
        if (!cur.isEmpty())
            rows.add(cur);
        return rows;
    }

    private static boolean endsSentence(String s) {
        if (s == null || s.isEmpty())
            return false;
        return SENTENCE_MARKS.indexOf(s.charAt(s.length() - 1)) >= 0;
    }

    /** Translucent backdrop: lightened (0.25 → 0.12). */
    private static int background() {
        float opacity = Minecraft.getInstance().options.getBackgroundOpacity(0.12F);
        return (int) (opacity * 255.0F) << 24;
    }

    /**
     * The lyrics of this musicPlayerId, fetched asynchronously: a getter per id, rebuilt when the track (the tracker's source) changed.
     */
    private static Lyric lyricOf(UUID musicPlayerId) {
        var engine = MusicEngine.getInstance();
        var entry = engine.getMusicEntry(musicPlayerId);
        if (entry == null)
            return null;
        var source = entry.getSource();
        if (source == null || MusicSource.EMPTY.equals(source))
            return null;

        var cache = CACHES.get(musicPlayerId);
        if (cache == null || !sameSource(cache.source, source)) {
            if (cache != null && cache.getter != null)
                cache.getter.stop();
            cache = new Cache(source, IMPLyricGetter.getGetter(source));
            CACHES.put(musicPlayerId, cache);
            if (cache.getter != null) {
                try {
                    cache.getter.run(source);
                } catch (Throwable t) {
                    LinkDebug.log("BlockLyric getter failed", "ex", t.getClass().getSimpleName() + ": " + t.getMessage());
                    cache.getter = null;
                }
            }
            if (cache.getter == null)
                LinkDebug.log("BlockLyric no getter", "loaderType", source.getLoaderType(),
                        "id", source.getIdentifier());
        }

        if (cache.getter != null && cache.getter.isFinish()) {
            cache.lyric = cache.getter.getLyric();
            cache.getter = null;
        }
        return cache.lyric;
    }

    private static boolean sameSource(MusicSource a, MusicSource b) {
        return a == b || (a != null && a.equals(b));
    }

    /** One per track: source, getter, fetched lyrics. */
    private static final class Cache {
        private final MusicSource source;
        private LyricGetter getter;
        private Lyric lyric;

        private Cache(MusicSource source, LyricGetter getter) {
            this.source = source;
            this.getter = getter;
        }
    }
}
