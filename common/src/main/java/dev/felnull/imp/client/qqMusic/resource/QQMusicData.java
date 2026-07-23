package dev.felnull.imp.client.qqMusic.resource;

import com.google.gson.annotations.SerializedName;
import dev.felnull.imp.music.resource.Lyric;
import it.unimi.dsi.fastutil.floats.Float2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.floats.Float2ObjectSortedMap;
import joptsimple.internal.Strings;
import org.jetbrains.annotations.Nullable;
import oshi.util.tuples.Pair;

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

        // todo:翻译歌词未实现
        Float2ObjectSortedMap<String> lyricMap = new Float2ObjectLinkedOpenHashMap<>();
        for (String sub: lyricString.split("\n")){
            var pair = getLyricPair(sub);
            if(pair != null)lyricMap.put(pair.getA(), pair.getB());
        }
        return new Lyric(lyricMap);
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
            int milliseconds = Integer.parseInt(matcher.group(3));
            String text = matcher.group(4);

            float totalSeconds = minutes * 60 + seconds + milliseconds / 1000f;
            return new Pair<>(totalSeconds, text.trim());
        }
        return null;
    }
}
