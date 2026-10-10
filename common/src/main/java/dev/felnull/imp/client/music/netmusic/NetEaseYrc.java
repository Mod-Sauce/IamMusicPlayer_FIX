package dev.felnull.imp.client.music.netmusic;

import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.music.resource.Lyric;
import it.unimi.dsi.fastutil.floats.Float2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.floats.Float2ObjectSortedMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * NetEase per-word lyrics (yrc) → {@link Lyric.Word}, attached to the keys of the **existing line table**: the two time
 * axes differ by up to 1.4 s, and storing words under yrc's own line times would leave {@link Lyric#getWords} without a
 * key and fall back to the previous line (the HUD would draw the wrong words). No Minecraft types, so it can be tested
 * outside the game.
 */
public final class NetEaseYrc {
    private NetEaseYrc() {
    }

    /** A line header {@code [start,length]}; property lines ({@code [ti:]} etc.) and blank lines do not match and are skipped naturally. */
    private static final Pattern LINE = Pattern.compile("^\\[(\\d+),(\\d+)\\](.*)$");
    /** A word marker {@code (start,length,0)}; the third number is always 0 and its meaning is unknown, so it is ignored. */
    private static final Pattern WORD = Pattern.compile("\\((\\d+),(\\d+),(\\d+)\\)");

    /** Pairing window in milliseconds: the two line-time axes can differ by up to 1.4 s. */
    public static final long MATCH_WINDOW_MS = 1500;

    /**
     * @param yrc          the raw yrc text ({@code data.yrc.lyric}); empty/null ⇒ null
     * @param offsetMillis lrc's {@code [offset:N]}, which the line table has **already** applied and yrc has not
     * @param lyrics       the line table (key = seconds, ascending); the per-word result is produced in its key order
     * @return one word entry per line; no usable yrc ⇒ null (the caller keeps the whole-line behaviour)
     */
    @Nullable
    public static Float2ObjectSortedMap<List<Lyric.Word>> attach(@Nullable String yrc, int offsetMillis,
                                                                 @Nullable Float2ObjectSortedMap<String> lyrics) {
        if (yrc == null || yrc.isBlank() || lyrics == null || lyrics.isEmpty())
            return null;
        var lines = parse(yrc);
        if (lines.isEmpty())
            return null;

        var keys = new ArrayList<Float>(lyrics.keySet());
        var texts = new ArrayList<String>(lyrics.values());
        var out = new Float2ObjectLinkedOpenHashMap<List<Lyric.Word>>();
        int matched = 0;
        int j = 0;   // 两边都升序：配不上时只可能往后走，所以一个游标够
        for (int i = 0; i < keys.size(); i++) {
            var key = keys.get(i);
            var text = texts.get(i);
            // A blank line is NetEase's placeholder for an interlude: letting it pair would steal the yrc line the next row needs
            if (text == null || text.isBlank()) {
                out.put(key, List.of());
                continue;
            }
            long rawLineMs = Math.round(key * 1000f) + offsetMillis;   // 还原成未加 offset 的毫秒，与 yrc 同尺
            while (j < lines.size() && lines.get(j).startMs() < rawLineMs - MATCH_WINDOW_MS)
                j++;
            Line hit = null;
            if (j < lines.size() && Math.abs(lines.get(j).startMs() - rawLineMs) <= MATCH_WINDOW_MS) {
                hit = lines.get(j);
                j++;
            }
            if (hit != null) {
                out.put(key, shift(hit.words(), offsetMillis));
                matched++;
            } else {
                // A 0 length stays "already sung" (white), matching how a whole line looks without per-word data
                out.put(key, List.of(new Lyric.Word(text, key, 0f)));
            }
        }
        LinkDebug.log("NetEase yrc", "lines", keys.size(), "yrcLines", lines.size(),
                "withWords", matched, "wholeLine", keys.size() - matched);
        return out;
    }

    /** One yrc line: the header in raw milliseconds plus the word list converted to seconds (**without** the offset). */
    private record Line(long startMs, long durationMs, List<Lyric.Word> words) {
    }

    private static List<Line> parse(String yrc) {
        var out = new ArrayList<Line>();
        for (var rawLine : yrc.split("\n")) {
            var matcher = LINE.matcher(rawLine.strip());
            if (!matcher.matches())
                continue;
            long startMs = parseLong(matcher.group(1), 0);
            long durationMs = parseLong(matcher.group(2), 0);
            var words = parseWords(matcher.group(3), startMs);
            if (!words.isEmpty())
                out.add(new Line(startMs, durationMs, words));
        }
        return out;
    }

    /** The text after a marker belongs to **that** marker (word) until the next one; any remainder before the first marker joins the first word. */
    private static List<Lyric.Word> parseWords(String content, long lineStartMs) {
        var marks = new ArrayList<long[]>();   // {标记起点, 标记终点, 起ms, 长ms}
        var matcher = WORD.matcher(content);
        while (matcher.find())
            marks.add(new long[]{matcher.start(), matcher.end(),
                    parseLong(matcher.group(1), lineStartMs), parseLong(matcher.group(2), 0)});
        if (marks.isEmpty()) {
            var text = content.strip();
            return text.isEmpty() ? List.of() : List.of(new Lyric.Word(text, lineStartMs / 1000f, 0f));
        }

        var lead = content.substring(0, (int) marks.get(0)[0]);
        var out = new ArrayList<Lyric.Word>(marks.size());
        for (int i = 0; i < marks.size(); i++) {
            var mark = marks.get(i);
            int to = i + 1 < marks.size() ? (int) marks.get(i + 1)[0] : content.length();
            var text = (i == 0 ? lead : "") + content.substring((int) mark[1], to);
            if (text.isEmpty())
                continue;
            out.add(new Lyric.Word(text, mark[2] / 1000f, mark[3] / 1000f));
        }
        return out;
    }

    /** yrc's word times are raw milliseconds while the line table is offset seconds, so only the start has to move. */
    private static List<Lyric.Word> shift(List<Lyric.Word> words, int offsetMillis) {
        var out = new ArrayList<Lyric.Word>(words.size());
        for (var word : words)
            out.add(new Lyric.Word(word.text(), Lyric.applyLrcOffset(word.start(), offsetMillis), word.duration()));
        return out;
    }

    private static long parseLong(String text, long def) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return def;
        }
    }
}
