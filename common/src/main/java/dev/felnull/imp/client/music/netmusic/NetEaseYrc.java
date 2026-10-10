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
 * 网易云逐字歌词（yrc）→ {@link Lyric.Word}，挂在**已有行表**的键上：两套时间轴差可达 1.4 秒，按 yrc 自己的行时间存词会
 * 让 {@link Lyric#getWords} 找不到键、退到前一行（HUD 画出上一行的字）。无 Minecraft 类型依赖，可脱离游戏自测。
 */
public final class NetEaseYrc {
    private NetEaseYrc() {
    }

    /** 行头 {@code [起,长]}；属性行（{@code [ti:]} 等）与空行不匹配，自然跳过。 */
    private static final Pattern LINE = Pattern.compile("^\\[(\\d+),(\\d+)\\](.*)$");
    /** 字标记 {@code (起,长,0)}；第三个数字恒为 0、含义未知，忽略。 */
    private static final Pattern WORD = Pattern.compile("\\((\\d+),(\\d+),(\\d+)\\)");

    /** 配对窗口（毫秒）：两套行时间的差可以到 1.4 秒。 */
    public static final long MATCH_WINDOW_MS = 1500;

    /**
     * @param yrc          原始 yrc 文本（{@code data.yrc.lyric}），空/null ⇒ null
     * @param offsetMillis lrc 的 {@code [offset:N]}，行表的时间**已经**加过它，yrc 的没有
     * @param lyrics       行表（键 = 秒，升序），逐字结果就按它的键与顺序产出
     * @return 每行一个词的列表；没有可用的 yrc ⇒ null（调用方维持原来的整行行为）
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
            // 空行是网易云的间奏占位：放它们去配对会把下一行该用的 yrc 行抢走
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
                // 时长 0 ⇒ 恒为"已唱"色（白），与不逐字时的整行观感一致
                out.put(key, List.of(new Lyric.Word(text, key, 0f)));
            }
        }
        LinkDebug.log("NetEase yrc", "lines", keys.size(), "yrcLines", lines.size(),
                "withWords", matched, "wholeLine", keys.size() - matched);
        return out;
    }

    /** 一条 yrc 行：原始毫秒的行头 + 已换算成秒（但**未加 offset**）的词表。 */
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

    /** 一个标记之后的文本归**这个**标记（字），一直到下一个标记之前；标记前的零头并进第一个字。 */
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

    /** yrc 的词时间是原始毫秒、行表的是加过 offset 的秒，只有起点要跟着移。 */
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
