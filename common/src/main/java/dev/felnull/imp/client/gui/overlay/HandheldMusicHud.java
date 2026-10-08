package dev.felnull.imp.client.gui.overlay;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.IMPConfig;
import dev.felnull.imp.block.BoomboxData;
import dev.felnull.imp.client.bilibili.BiliBiliParts;
import dev.felnull.imp.client.music.lyric.IMPLyricGetter;
import dev.felnull.imp.client.music.lyric.LyricGetter;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.music.resource.Lyric;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.modsauce.otyacraftenginerenewed.client.util.OEClientUtils;

/**
 * Bottom-centre HUD for the **handheld** boombox ("手提音响"): song line + lyric line (karaoke when the lyrics carry
 * per-word timings), each with its own switch, plus a song-change popup that only shows while the song line is off.
 */
public class HandheldMusicHud {
    private static final Minecraft mc = Minecraft.getInstance();
    /** Fade-out length of the song-change popup (ms). */
    private static final long POPUP_FADE_MS = 600L;

    private LyricGetter lyricGetter;
    private Lyric lyric;
    private MusicSource lyricSource;

    /** Key of the song currently shown, and when it changed (drives the popup). */
    private String shownSongKey;
    private long songChangedAt;

    public void render(GuiGraphics guiGraphics, BoomboxData data) {
        var cfg = IamMusicPlayer.getConfig();
        // Not on top of the F3 debug screen (vanilla's own subtitles hide there as well)
        if (mc.getDebugOverlay().showDebugScreen())
            return;

        var music = data.getSelectedMusic() == null ? data.getCassetteTapeMusic() : data.getSelectedMusic();
        if (music == null)
            return;

        trackSongChange(music);
        updateLyric(music.getSource(), cfg.hudBottomLyricLine);

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int lyricLineY = bottomLineY();
        int songLineY = topLineY();
        int maxWidth = Math.max(60, cfg.hudBottomMaxWidth);
        var style = cfg.hudBottomStyle;

        if (cfg.hudBottomSongLine) {
            drawLine(guiGraphics, Component.literal(songText(music)), screenWidth / 2, songLineY, 0xFFD0D0D0, maxWidth, style, 1f);
        } else if (cfg.hudSongChangePopup && shownSongKey != null) {
            float alpha = popupAlpha(cfg.hudSongChangeSeconds);
            if (alpha > 0f)
                // Text is built while drawing, not at change time: the part marker is only learned a moment after
                // the track starts, i.e. exactly within the popup's short life.
                drawLine(guiGraphics, Component.literal(songText(music)), screenWidth / 2, songLineY, 0xFFD0D0D0, maxWidth, style, alpha);
        }

        if (cfg.hudBottomLyricLine && lyric != null && !lyric.isEmpty()) {
            var position = data.getMusicPosition() / 1000f - Lyric.lyricOffsetSeconds();
            var part = lyric.getPart(position);
            if (part != null && part.getA() != null && !part.getA().isBlank()) {
                // Karaoke only on tracks whose lyrics really carry per-character timings; the rest keep the plain line,
                // so the switch can never leave the line empty.
                var words = cfg.hudBottomKaraoke ? lyric.getWords(position) : null;
                if (words != null && !words.isEmpty())
                    drawKaraokeLine(guiGraphics, words, position, screenWidth / 2, lyricLineY, maxWidth, style, 1f);
                else
                    drawLine(guiGraphics, Component.literal(part.getA()), screenWidth / 2, lyricLineY, 0xFFFFFFFF, maxWidth, style, 1f);
            }
        }
    }

    private static String songText(Music music) {
        var author = music.getAuthor();
        var text = author == null || author.isBlank() ? music.getName() : music.getName() + " — " + author;
        // [分P] Part marker of a multi-part B站 video (`P2/2`); parts are only known once the video was played or
        // looked up. It must stay at the front — a trailing marker is cut off by `hudBottomMaxWidth`.
        var source = music.getSource();
        if (source != null && IMPMusicMedias.BILIBILI.getName().equals(source.getLoaderType())) {
            var label = BiliBiliParts.pageLabel(source.getIdentifier(), BiliBiliParts.known(source.getIdentifier()));
            if (!label.isEmpty())
                text = label + " · " + text;
        }
        return text;
    }

    /** Records "the track just changed" for the popup (also fires once when the world is joined). */
    private void trackSongChange(Music music) {
        var source = music.getSource();
        var key = source == null ? music.getName() : source.getLoaderType() + ":" + source.getIdentifier();
        if (key.equals(shownSongKey))
            return;
        shownSongKey = key;
        songChangedAt = System.currentTimeMillis();
    }

    /** 1 while the popup is fully visible, ramping to 0 over the last {@link #POPUP_FADE_MS}. */
    private float popupAlpha(int seconds) {
        long shown = System.currentTimeMillis() - songChangedAt;
        long total = Math.max(1, seconds) * 1000L;
        if (shown >= total)
            return 0f;
        long left = total - shown;
        return left >= POPUP_FADE_MS ? 1f : (float) left / (float) POPUP_FADE_MS;
    }

    /** Keeps one lyric getter alive per source; with the lyric line off no getter is started at all. */
    private void updateLyric(MusicSource source, boolean lyricsEnabled) {
        var sameSource = lyricSource != null && lyricSource.equals(source);
        if (!sameSource) {
            stopGetter();
            lyric = null;
            lyricSource = source;
        }
        if (!lyricsEnabled || source == null)
            return;
        if (lyricGetter != null && lyricGetter.isFinish()) {
            lyric = lyricGetter.getLyric();
            lyricGetter = null;
        }
        if (lyric == null && lyricGetter == null) {
            var getter = IMPLyricGetter.getGetter(source);
            if (getter != null) {
                lyricGetter = getter;
                lyricGetter.run(source);
            }
        }
    }

    private void stopGetter() {
        if (lyricGetter != null) {
            lyricGetter.stop();
            lyricGetter = null;
        }
    }

    /** Y of the lower (lyric) line; floored at {@code 60 + lineHeight + 1} so the vanilla item-name band stays clear. */
    public static int bottomLineY() {
        int minMargin = 60 + mc.font.lineHeight + 1;
        return mc.getWindow().getGuiScaledHeight() - Math.max(minMargin, IamMusicPlayer.getConfig().hudBottomMargin);
    }

    /** Y of the **upper** bottom line (song name / part line), one line above {@link #bottomLineY()}. */
    public static int topLineY() {
        return bottomLineY() - mc.font.lineHeight;
    }

    /** One centred line, cut to {@code maxWidth} with an ellipsis; {@code public static} so the 「跳分P」 line shares it. */
    public static void drawLine(GuiGraphics guiGraphics, Component text, int centerX, int y, int color, int maxWidth,
                                IMPConfig.HudBottomStyle style, float alpha) {
        var shown = OEClientUtils.getWidthOmitText(text.getString(), maxWidth, "...");
        int width = mc.font.width(shown);
        int x = centerX - width / 2;
        int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));

        drawBackdrop(guiGraphics, x, width, y, style, a);
        // PLAIN keeps the vanilla text shadow; the styles with a background draw flat (the background separates them)
        guiGraphics.drawString(mc.font, shown, x, y, (a << 24) | (color & 0xFFFFFF), style == IMPConfig.HudBottomStyle.PLAIN);
    }

    /** Background of one bottom-HUD line, shared with the karaoke line; {@code PLAIN} draws nothing. */
    private static void drawBackdrop(GuiGraphics guiGraphics, int x, int width, int y, IMPConfig.HudBottomStyle style, int a) {
        switch (style) {
            case STRIP -> guiGraphics.fill(x - 2, y - 1, x + width + 2, y + mc.font.lineHeight, (a * 0x60 / 255) << 24);
            case BOX -> {
                // Like the vanilla item-name tooltip: a border fill with a darker interior
                guiGraphics.fill(x - 4, y - 4, x + width + 4, y + mc.font.lineHeight + 3, (a << 24) | 0x5000FF);
                guiGraphics.fill(x - 3, y - 3, x + width + 3, y + mc.font.lineHeight + 2, (a << 24) | 0x100010);
            }
            case PLAIN -> {
            }
        }
    }

    /** As the plain line, but each word in its own colour ({@link HudKaraokeLine}); backdrop sized from the segments. */
    private static void drawKaraokeLine(GuiGraphics guiGraphics, java.util.List<Lyric.Word> words, float position,
                                        int centerX, int y, int maxWidth, IMPConfig.HudBottomStyle style, float alpha) {
        var segments = HudKaraokeLine.segments(words, position, maxWidth, mc.font::width);
        if (segments.isEmpty())
            return;
        int width = 0;
        for (var segment : segments)
            width += mc.font.width(segment.text());
        int x = centerX - width / 2;
        int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));

        drawBackdrop(guiGraphics, x, width, y, style, a);
        boolean shadow = style == IMPConfig.HudBottomStyle.PLAIN;
        int px = x;
        for (var segment : segments) {
            guiGraphics.drawString(mc.font, segment.text(), px, y, (a << 24) | (segment.color() & 0xFFFFFF), shadow);
            px += mc.font.width(segment.text());
        }
    }
}
