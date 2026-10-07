package dev.felnull.imp.music.resource;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.cache.LyricDTO;
import it.unimi.dsi.fastutil.floats.Float2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.floats.Float2ObjectSortedMap;
import org.jetbrains.annotations.Nullable;
import oshi.util.tuples.Pair;

import java.util.List;
import java.util.regex.Pattern;

public class Lyric {
    private final Float2ObjectSortedMap<String> lyrics;
    @Nullable
    private final Float2ObjectSortedMap<String> transLyrics;
    /** Per-character data (line start seconds → word list of that line); only kugou krc provides it, null for other channels. */
    @Nullable
    private final Float2ObjectSortedMap<List<Word>> wordLyrics;
    /** Key/value snapshots, built once: both lookups run per frame, and the maps never change after construction. */
    private final List<Float> keyList;
    private final List<String> valueList;
    public static final Lyric EMPTY = new Lyric();

    public Lyric(Float2ObjectSortedMap<String> lyrics, @Nullable Float2ObjectSortedMap<String> transLyrics){
        this(lyrics, transLyrics, null);
    }

    public Lyric(Float2ObjectSortedMap<String> lyrics, @Nullable Float2ObjectSortedMap<String> transLyrics,
                 @Nullable Float2ObjectSortedMap<List<Word>> wordLyrics){
        this.lyrics = lyrics;
        this.transLyrics = transLyrics;
        this.wordLyrics = wordLyrics;
        this.keyList = lyrics == null ? List.of() : lyrics.keySet().stream().toList();
        this.valueList = lyrics == null ? List.of() : lyrics.values().stream().toList();
    }

    public Lyric(Float2ObjectSortedMap<String> lyrics){
        this(lyrics, null, null);
    }

    public Lyric(){
        this(null, null, null);
    }

    public boolean isEmpty(){
        return lyrics == null;
    }

    /** One word with its absolute start and end in the whole song (seconds). */
    public record Word(String text, float start, float duration) {
    }

    @SuppressWarnings("all")
    public Pair<String, String> getPart(float second){
        if (keyList.isEmpty() || second < keyList.get(0))
            return new Pair<String, String>("", null);   // before the first line there is nothing; the loop would fall through to the last line
        var text = "";
        var lyricTime = 0f;
        String transformText = null;
        for (int i = 0; i < keyList.size(); i++) {
            if (i == keyList.size() - 1) {
                text = valueList.get(i);
                lyricTime = keyList.get(i);
                break;
            }
            if (keyList.get(i) <= second && keyList.get(i + 1) > second) {
                text = valueList.get(i);
                lyricTime = keyList.get(i);
                break;
            }
        }
        if (transLyrics != null) {
            transformText = transLyrics.getOrDefault(lyricTime, null);

            // [Bad-data fallback] When a line's **translation is identical to the previous line while the
            // original text has changed** ⇒ **do not show the translation on this line**.
            var idx = keyList.indexOf(lyricTime);
            if (transformText != null && idx > 0) {
                var prevText = valueList.get(idx - 1);
                if (!prevText.equals(text)
                        && transformText.equals(transLyrics.get(keyList.get(idx - 1))))
                    transformText = null;
            }
        }
        return new Pair(text, transformText);
    }

    public boolean hasTrans(){
        return transLyrics != null && !transLyrics.isEmpty();
    }

    /** Whether per-character data exists (decides whether the renderer uses "per-character karaoke" or "whole line"). */
    public boolean hasWords(){
        return wordLyrics != null && !wordLyrics.isEmpty();
    }

    /** Word list of the current line ({@code second} falls on it); null when there is no per-character data. */
    @Nullable
    public List<Word> getWords(float second){
        // ⚠️ `lyrics` must be checked too (constructor allows null, e.g. `Lyric#EMPTY`): the keySet() walk below would NPE
        // on the world render thread; a word map without a line map is unusable data anyway.
        if (wordLyrics == null || wordLyrics.isEmpty() || lyrics == null || lyrics.isEmpty())
            return null;
        if (second < keyList.get(0))
            return null;                                    // no line yet: must not fall through to the last one either
        // Same "find the current line" logic as getPart (keyList[i] <= second < keyList[i+1], last line as fallback)
        var lyricTime = 0f;
        for (int i = 0; i < keyList.size(); i++) {
            if (i == keyList.size() - 1) {
                lyricTime = keyList.get(i);
                break;
            }
            if (keyList.get(i) <= second && keyList.get(i + 1) > second) {
                lyricTime = keyList.get(i);
                break;
            }
        }
        var line = wordLyrics.get(lyricTime);
        // ⚠️ Exact-key miss (e.g. krc word time drifting from the line time) must not silently drop to the whole line:
        // fall back to the word line that starts at or before this line's time.
        if (line != null)
            return line;
        var fallback = wordLyrics.headMap(lyricTime);
        return fallback.isEmpty() ? null : fallback.get(fallback.lastFloatKey());
    }

    // ------------------------------------------------------------------
    // LRC text helpers, shared by the QQ and the NetEase lyric chains
    // ------------------------------------------------------------------

    /** LRC header field {@code [offset:N]} (milliseconds, may be negative). */
    private static final Pattern LRC_OFFSET = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);

    /** Production-crew keywords: a "role: value" line is a credit when its role carries one of these.
     * Deliberately broad — credits are free text, so any role word naming a job counts; false positives are handled by
     * {@link #SPEAKER_ROLES} and by matching the role position only, not the whole line. */
    private static final String[] CREDIT_KEYWORDS = {"作词", "作曲", "编曲", "制作", "监制", "混音", "母带", "录音",
            "和声", "合声", "吉他", "贝斯", "钢琴", "键盘", "鼓", "弦乐", "管乐", "管弦", "民乐", "打击乐", "口琴",
            "萨克斯", "长笛", "小号", "大提琴", "小提琴", "中提琴", "低音", "出品", "发行", "统筹", "企划", "策划",
            "宣传", "营销", "封面", "文案", "编辑", "总监", "助理", "鸣谢", "录音室", "录音棚", "配唱", "伴唱",
            "艺统", "导演", "摄影", "剪辑", "视觉", "设计", "插画", "字体", "翻译", "校对", "念白", "后期",
            "混音师", "母带师", "制作人", "音乐总监", "声乐", "指导", "声部", "童声", "人声", "采样", "编写",
            "演奏", "乐队", "乐团", "合唱团", "出品人", "制作统筹", "录制", "工程师", "编程", "推广", "词", "曲"};

    /** Copyright / claiming keywords: a line carrying one of these is a credit line (no colon required). */
    private static final String[] COPYRIGHT_KEYWORDS = {"版权", "未经许可", "腾讯", "Credit", "本歌曲", "声明"};

    /** Roles that are credits without being crew words (NetEase writes e.g. {@code OP : Amuse Inc.}). */
    private static final String[] CREDIT_ROLES = {"op", "sp", "isrc"};

    /** Roles that introduce a **person** (speaker / artist) instead of a production credit, see {@link #isCreditLine}. */
    private static final String[] SPEAKER_ROLES = {"歌手", "演唱", "演唱者", "合唱", "主唱", "对唱", "男声", "女声",
            "童声", "念白", "旁白", "artist", "artists", "singer", "singers", "vocal", "vocals", "chorus", "feat", "ft"};

    /** Reads the LRC header field {@code [offset:N]} in ms (may be negative), or {@code defMillis} when
     * absent/unparsable; the whole text is scanned, not just the header — some providers write it after the
     * metadata block; a line without {@code mm:ss} is never a lyric. */
    public static int getLrcOffsetMillis(@Nullable String lrc, int defMillis) {
        if (lrc == null) return defMillis;
        var matcher = LRC_OFFSET.matcher(lrc);
        if (!matcher.find()) return defMillis;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return defMillis;
        }
    }

    /** Shifts an LRC timestamp by the LRC offset (Lyricify {@code startTime -= offset/1000}); negatives clamp to 0,
     * and {@code offsetMillis == 0} returns the value bit-for-bit, keeping QQ's original/translation pairing intact. */
    public static float applyLrcOffset(float seconds, int offsetMillis) {
        float t = seconds - offsetMillis / 1000f;
        return t < 0f ? 0f : t;
    }

    /**
     * Whether the credit-line filter is on. The config is read defensively — lyrics are parsed on loader threads and an old config file may lack the field — so any failure falls back to {@code IMPConfig#filterCreditLines}'s default.
     */
    public static boolean isCreditLineFilterEnabled() {
        try {
            return IamMusicPlayer.getConfig().filterCreditLines;
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** Timing compensation in seconds ({@code IMPConfig#lyricOffsetMs}), subtracted from the playback position before a
     * line/word lookup. Audio lags behind by device latency (Bluetooth: ~150–300 ms), unnoticeable on lines, obvious
     * with per-word karaoke; positive values delay lyrics. Defensive read: {@link #isCreditLineFilterEnabled()}. */
    public static float lyricOffsetSeconds() {
        try {
            return IamMusicPlayer.getConfig().lyricOffsetMs / 1000f;
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    /** A timestamped line is a credit, not a lyric (Lyricify {@code InfoLines.IsInfoLine}): a copyright keyword, or
     * a "role: value" role in {@code OP}/{@code SP}/{@code ISRC} or a crew keyword, matched on the role only, not the
     * whole line (whole-line matching drops real lyrics); 歌手/演唱/A: xxx stay lyrics (LooksLikeArtistSpeakerLabel). */
    public static boolean isCreditLine(@Nullable String rawText) {
        if (rawText == null) return false;
        var text = rawText.replace('：', ':').trim();
        if (text.isEmpty()) return false;
        int colon = text.indexOf(':');
        var role = colon > 0 ? text.substring(0, colon).trim() : "";
        boolean roleValue = colon > 0 && colon + 1 < text.length();   // "role: value" shape
        if (roleValue && isSpeakerRole(role)) return false;           // "歌手: 张三" is a lyric line
        for (var k : COPYRIGHT_KEYWORDS)
            if (text.contains(k)) return true;
        if (!roleValue) return false;
        for (var r : CREDIT_ROLES)
            if (role.equalsIgnoreCase(r)) return true;
        for (var k : CREDIT_KEYWORDS)
            if (role.contains(k)) return true;
        return false;
    }

    /** {@code true} for a speaker/artist label: one ASCII letter or digit, or one of {@link #SPEAKER_ROLES}. */
    private static boolean isSpeakerRole(String role) {
        if (role.length() == 1) {
            char c = role.charAt(0);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) return true;
        }
        for (var r : SPEAKER_ROLES)
            if (role.equalsIgnoreCase(r)) return true;
        return false;
    }

    public static LyricDTO toDTO(Lyric lyric) {
        if (lyric == null || lyric.isEmpty()) return null;

        LyricDTO dto = new LyricDTO();

        lyric.lyrics.forEach((time, text) -> {
            dto.lyrics.add(new LyricDTO.LyricEntry(time, text));
        });

        if (lyric.transLyrics != null) {
            lyric.transLyrics.forEach((time, text) -> {
                dto.transLyrics.add(new LyricDTO.LyricEntry(time, text));
            });
        }

        if (lyric.wordLyrics != null) {
            lyric.wordLyrics.forEach((time, words) -> {
                var line = new LyricDTO.WordLine();
                line.time = time;
                for (var w : words)
                    line.words.add(new LyricDTO.Word(w.text(), w.start(), w.duration()));
                dto.wordLyrics.add(line);
            });
        }

        return dto;
    }

    public static Lyric fromDTO(LyricDTO dto) {
        if (dto == null) return Lyric.EMPTY;

        Float2ObjectSortedMap<String> lyrics = new Float2ObjectLinkedOpenHashMap<>();
        Float2ObjectSortedMap<String> trans = new Float2ObjectLinkedOpenHashMap<>();

        if (dto.lyrics != null) {
            for (LyricDTO.LyricEntry e : dto.lyrics) {
                lyrics.put(e.time, e.text);
            }
        }

        if (dto.transLyrics != null) {
            for (LyricDTO.LyricEntry e : dto.transLyrics) {
                trans.put(e.time, e.text);
            }
        }

        Float2ObjectSortedMap<List<Word>> words = null;
        if (dto.wordLyrics != null && !dto.wordLyrics.isEmpty()) {
            words = new Float2ObjectLinkedOpenHashMap<>();
            for (var line : dto.wordLyrics) {
                var list = new java.util.ArrayList<Word>();
                for (var w : line.words)
                    list.add(new Word(w.text, w.start, w.duration));
                words.put(line.time, list);
            }
        }

        return new Lyric(lyrics, trans.isEmpty() ? null : trans, words);
    }
}
