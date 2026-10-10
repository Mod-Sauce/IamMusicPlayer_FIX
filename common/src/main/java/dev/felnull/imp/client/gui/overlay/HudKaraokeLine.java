package dev.felnull.imp.client.gui.overlay;

import dev.felnull.imp.client.renderer.BlockLyricRenderer;
import dev.felnull.imp.music.resource.Lyric;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Layout of the bottom HUD karaoke line: the current line is cut into coloured spans whose three colours come from
 * {@link BlockLyricRenderer} (so both places cannot drift apart). A word that does not fit is dropped and marked with
 * {@value #ELLIPSIS}, never cut in half. No Minecraft types, so it can be tested outside the game.
 */
public final class HudKaraokeLine {
    private HudKaraokeLine() {
    }

    /** Marks the dropped tail, spelled like the rest of the HUD ({@code OEClientUtils.getWidthOmitText}). */
    public static final String ELLIPSIS = "...";

    /** One run of characters sharing one colour. */
    public record Segment(String text, int color) {
    }

    /**
     * @param words    the current line's per-character data ({@link Lyric#getWords(float)}); empty/null → empty result
     * @param position playback position in seconds, already offset-compensated
     * @param maxWidth width cap in px (the HUD's own {@code hudBottomMaxWidth})
     * @param widthOf  text width in px (the game passes {@code mc.font::width})
     */
    public static List<Segment> segments(List<Lyric.Word> words, float position, int maxWidth,
                                         ToIntFunction<String> widthOf) {
        var out = new ArrayList<Segment>();
        if (words == null || words.isEmpty())
            return out;

        int total = 0;
        for (var word : words) {
            if (word.text() != null)
                total += widthOf.applyAsInt(word.text());
        }
        // Only reserve room for the ellipsis when something will actually be dropped, so a fitting line is untouched.
        boolean truncated = total > maxWidth;
        // ...and only add it when it fits: at a degenerate width the ellipsis alone would already break the cap
        // (the HUD floors its cap at 60 px, so this is just the can't-happen case handled honestly).
        boolean markCut = truncated && maxWidth >= widthOf.applyAsInt(ELLIPSIS);
        int budget = truncated ? (markCut ? maxWidth - widthOf.applyAsInt(ELLIPSIS) : maxWidth) : maxWidth;

        int width = 0;
        for (var word : words) {
            var text = word.text();
            if (text == null || text.isEmpty())
                continue;
            int wordWidth = widthOf.applyAsInt(text);
            if (width + wordWidth > budget)
                break;
            width += wordWidth;
            append(out, text, colorOf(word, position));
        }
        if (markCut)
            append(out, ELLIPSIS, BlockLyricRenderer.ORIGINAL_COLOR);
        return out;
    }

    /** The colour of one word at {@code position}: sung, being sung, or not reached yet. */
    private static int colorOf(Lyric.Word word, float position) {
        if (position >= word.start() + word.duration())
            return BlockLyricRenderer.SUNG_WORD_COLOR;
        if (position >= word.start())
            return BlockLyricRenderer.ACTIVE_WORD_COLOR;
        return BlockLyricRenderer.ORIGINAL_COLOR;
    }

    /** Neighbouring words of the same colour are merged, so one line costs a few draw calls instead of one per character. */
    private static void append(List<Segment> out, String text, int color) {
        if (!out.isEmpty() && out.get(out.size() - 1).color() == color) {
            var last = out.get(out.size() - 1);
            out.set(out.size() - 1, new Segment(last.text() + text, color));
            return;
        }
        out.add(new Segment(text, color));
    }
}
