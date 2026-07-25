package dev.felnull.imp.client.qqMusic.resource;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class QQSearchData {
    @SerializedName("req_0")
    public QQSearchInfo searchInfo;

    public static class QQSearchInfo {
        @SerializedName("code")
        public int code;

        @SerializedName("data")
        public SearchData data;
    }

    public static class SearchData {
        @SerializedName("body")
        public Body body;
    }

    public static class Body {
        @SerializedName("song")
        public SongData song;
    }

    public static class SongData {
        @SerializedName("list")
        public List<TrackInfo> songs;
    }

    public static class TrackInfo {
        @SerializedName("id")
        public long id;

        @SerializedName("mid")
        public String mid;

        @SerializedName("name")
        public String name;

        @SerializedName("subtitle")
        public String subtitle;

        @SerializedName("interval")
        public long length;

        @SerializedName("singer")
        public List<Singer> singers;

        @SerializedName("album")
        public Album album;

        @Nullable
        public String getSingerString() {
            if (singers == null || singers.isEmpty()) return null;
            return String.join("、", singers.stream().map(s -> s.name).toList());
        }

        @Nullable
        public String getPicUrl() {
            if (album == null || album.mid == null) return null;
            return String.format("https://y.qq.com/music/photo_new/T002R300x300M000%s.jpg", album.mid);
        }
    }

    public static class Album {
        @SerializedName("mid")
        public String mid;

        @SerializedName("name")
        public String name;
    }

    public static class Singer {
        @SerializedName("name")
        public String name;
    }

    public List<TrackInfo> getSongs() {
        if (searchInfo == null || searchInfo.data == null || searchInfo.data.body == null) return List.of();
        if (searchInfo.data.body.song == null) return List.of();
        return searchInfo.data.body.song.songs;
    }
}