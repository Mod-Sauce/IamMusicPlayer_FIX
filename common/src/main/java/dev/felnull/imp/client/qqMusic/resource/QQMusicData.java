package dev.felnull.imp.client.qqMusic.resource;

import com.google.gson.annotations.SerializedName;
import dev.felnull.imp.music.resource.Lyric;
import it.unimi.dsi.fastutil.floats.Float2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.floats.Float2ObjectSortedMap;
import joptsimple.internal.Strings;
import org.jetbrains.annotations.Nullable;
import oshi.util.tuples.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QQMusicData {
    @SerializedName("code")
    public int code;

    @SerializedName("songinfo")
    public QQSoundInfo soundInfo;

    public static class QQSoundInfo{
        @SerializedName("code")
        public int code;

        @SerializedName("data")
        public SoundInfoData soundInfo;
    }

    public static class SoundInfoData {
        @SerializedName("track_info")
        public TrackInfo trackInfo;

        @SerializedName("info")
        public List<OtherInfo> otherInfos;

        @Nullable
        public String getLyric(){
            if(otherInfos == null)return null;

            // [Trace, prints only while LinkDebug is on] Only the real lyric slots.
            if (dev.felnull.imp.client.music.link.LinkDebug.isEnabled()) {
                for (OtherInfo oi : otherInfos) {
                    if (!"lyric".equals(oi.type) && !"trans".equals(oi.type))
                        continue;
                    String head = (oi.contents == null || oi.contents.isEmpty()) ? null : oi.contents.getFirst().value;
                    dev.felnull.imp.client.music.link.LinkDebug.log("QQ lyric field", "type", oi.type,
                            "len", head == null ? 0 : head.length(),
                            "head", head == null ? "null" : head.substring(0, Math.min(160, head.length())));
                }
            }

            for (OtherInfo otherInfo: otherInfos){
                if(otherInfo.type.equals("lyric") && !otherInfo.contents.isEmpty()){
                    return otherInfo.contents.getFirst().value;
                }
            }
            return null;
        }
    }

    public static class TrackInfo {
        @SerializedName("mid")
        public String mid;

        @SerializedName("name")
        public String name;

        @SerializedName("subtitle")
        public String subTitle;

        @SerializedName("singer")
        public List<Singer> singers;

        @SerializedName("interval")
        public long musicLengthSecond;

        @SerializedName("album")
        public Album album;

        /** QQ keeps the playable file under its own media mid, so the file-name chain has to be built from that one. */
        @SerializedName("file")
        public FileInfo file;
    }

    public static class FileInfo{
        @SerializedName("media_mid")
        public String mediaMid;
    }

    public static class Album{
        @SerializedName("mid")
        public String mid;
    }

    public static class OtherInfo {
        @SerializedName("type")
        public String type;

        @SerializedName("content")
        public List<InfoContent> contents;
    }

    public static class InfoContent {
        @SerializedName("value")
        public String value;
    }

    public static class Singer {
        @SerializedName("name")
        public String name;
    }

    public String getMID(){
        if(soundInfo == null)return null;
        if(soundInfo.soundInfo == null)return null;
        if(soundInfo.soundInfo.trackInfo == null)return null;
        return soundInfo.soundInfo.trackInfo.mid;
    }

    /** Media mid of the playable file; null when QQ does not report one (the caller then falls back to the song mid). */
    @Nullable
    public String getMediaMID(){
        if(soundInfo == null)return null;
        if(soundInfo.soundInfo == null)return null;
        if(soundInfo.soundInfo.trackInfo == null)return null;
        if(soundInfo.soundInfo.trackInfo.file == null)return null;
        var mid = soundInfo.soundInfo.trackInfo.file.mediaMid;
        return mid == null || mid.isEmpty() ? null : mid;
    }

    @Nullable
    public String getName(){
        if(soundInfo == null)return null;
        if(soundInfo.soundInfo == null)return null;
        if(soundInfo.soundInfo.trackInfo == null)return null;
        var name = soundInfo.soundInfo.trackInfo.name;
        if(name == null || name.isEmpty())return null;
        return name;
    }

    @Nullable
    public String getSubtitle(){
        if(soundInfo == null)return null;
        if(soundInfo.soundInfo == null)return null;
        if(soundInfo.soundInfo.trackInfo == null)return null;
        var name = soundInfo.soundInfo.trackInfo.subTitle;
        if(name == null || name.isEmpty())return null;
        return name;
    }

    @Nullable
    public Long getSecond(){
        if(soundInfo == null)return null;
        if(soundInfo.soundInfo == null)return null;
        if(soundInfo.soundInfo.trackInfo == null)return null;
        return soundInfo.soundInfo.trackInfo.musicLengthSecond;
    }

    @Nullable
    public String getSingerString(){
        final String split = "、";
        if(soundInfo == null)return null;
        if(soundInfo.soundInfo == null)return null;
        if(soundInfo.soundInfo.trackInfo == null)return null;
        var result = Strings.join(soundInfo.soundInfo.trackInfo.singers.stream()
                .map(s -> s.name).toList(), split);
        if(result.isEmpty())return null;
        return result;
    }

    @Nullable
    public Lyric getLyric(){
        if(soundInfo == null)return null;
        if(soundInfo.soundInfo == null)return null;
        var lyricString = soundInfo.soundInfo.getLyric();
        if(lyricString == null)return null;

        // QQ embeds the translation inline as the following line with its own later timestamp, so one "timestamp -> text" map would show the two as separate lines instead of stacking them.
        // Fix: adjacent lines whose scripts differ are treated as original + translation, and the translation is stored under the original's timestamp so both render together.
        Float2ObjectSortedMap<String> lyricMap = new Float2ObjectLinkedOpenHashMap<>();
        Float2ObjectSortedMap<String> transMap = new Float2ObjectLinkedOpenHashMap<>();

        var entries = new ArrayList<Pair<Float, String>>();
        // [offset:±N] sits in the header block next to [ti:]/[ar:] and shifts **every** timestamp
        // (Lyricify's convention: `startTime -= offset`, offset in milliseconds, minus = earlier).
        // Without the tag applyLrcOffset returns the value bit-for-bit, so the pairing below is untouched.
        int offsetMillis = Lyric.getLrcOffsetMillis(lyricString, 0);
        for (String sub: lyricString.split("\n")){
            var pair = getLyricPair(sub);
            if (pair == null) continue;
            var text = pair.getB();
            if (text == null || text.isBlank()) continue;
            entries.add(new Pair<>(Lyric.applyLrcOffset(pair.getA(), offsetMillis), text));
        }

        for (int i = 0; i < entries.size(); i++){
            var cur = entries.get(i);
            var next = i + 1 < entries.size() ? entries.get(i + 1) : null;
            boolean sameStamp = next != null && next.getA().equals(cur.getA());
            boolean scriptPair = next != null && !sameStamp && cjkOnly(cur.getB()) != cjkOnly(next.getB());
            if (next != null && (sameStamp || scriptPair)){
                lyricMap.putIfAbsent(cur.getA(), cur.getB());
                transMap.putIfAbsent(cur.getA(), next.getB());
                i++;                                     // the next line was consumed as this line's translation
                continue;
            }
            lyricMap.putIfAbsent(cur.getA(), cur.getB());
        }

        // [credit-line filter] Runs after the pairing loop on purpose: filtering the entries first would re-pair the neighbours (an info line consumed as a translation is skipped by `i++`) and change which line is shown.
        if (Lyric.isCreditLineFilterEnabled()){
            var lyricIt = lyricMap.float2ObjectEntrySet().iterator();
            while (lyricIt.hasNext())
                if (Lyric.isCreditLine(lyricIt.next().getValue())) lyricIt.remove();
            var transIt = transMap.float2ObjectEntrySet().iterator();
            while (transIt.hasNext())
                if (Lyric.isCreditLine(transIt.next().getValue())) transIt.remove();
        }
        return new Lyric(lyricMap, transMap.isEmpty() ? null : transMap);
    }

    @Nullable
    public String getPicUrl(){
        if(soundInfo == null)return null;
        if(soundInfo.soundInfo == null)return null;
        if(soundInfo.soundInfo.trackInfo == null)return null;
        if(soundInfo.soundInfo.trackInfo.album == null)return null;
        final String base = "https://y.qq.com/music/photo_new/T002R300x300M000%s.jpg";
        return String.format(base, soundInfo.soundInfo.trackInfo.album.mid);
    }

    private static Pair<Float, String> getLyricPair(String input){
        Pattern pattern = Pattern.compile("^\\[(\\d+):(\\d+)[.:](\\d+)](.*)$");
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            int minutes = Integer.parseInt(matcher.group(1));
            int seconds = Integer.parseInt(matcher.group(2));
            String fraction = matcher.group(3);
            String text = matcher.group(4);

            // The fraction is not always milliseconds: QQ writes centiseconds (`mm:ss.xx`) while e.g. NetEase writes `mm:ss.xxx`.
            // Dividing by a fixed 1000 would fire each QQ line up to a second early by a varying amount, so the lyrics drift; scale by the digit count.
            float frac = Integer.parseInt(fraction) / (float) Math.pow(10, fraction.length());
            float totalSeconds = minutes * 60 + seconds + frac;
            return new Pair<>(totalSeconds, text.trim());
        }
        return null;
    }

    /**
     * Whether a lyric line is CJK-ideograph only (no Latin, kana or hangul), which is how an original is told apart from its inline translation.
     */
    private static boolean cjkOnly(String s){
        boolean han = false;
        for (int i = 0; i < s.length(); ){
            int c = s.codePointAt(i);
            i += Character.charCount(c);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) return false;
            if (c >= 0x3040 && c <= 0x30FF) return false;   // kana
            if (c >= 0x1100 && c <= 0x11FF) return false;   // hangul jamo
            if (c >= 0xAC00 && c <= 0xD7AF) return false;   // hangul syllables
            if ((c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF)) han = true;
        }
        return han;
    }
}
