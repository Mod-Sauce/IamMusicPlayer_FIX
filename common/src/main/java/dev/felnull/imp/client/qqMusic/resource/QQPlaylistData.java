package dev.felnull.imp.client.qqMusic.resource;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class QQPlaylistData {
    @SerializedName("req_0")
    public QQPlaylistInfo playlistInfo;

    public static class QQPlaylistInfo {
        @SerializedName("code")
        public int code;

        @SerializedName("data")
        public PlaylistData data;
    }

    public static class PlaylistData {
        @SerializedName("dirinfo")
        public DirInfo dirInfo;

        @SerializedName("songlist")
        public List<TrackInfo> songs;
    }

    public static class DirInfo {
        @SerializedName("id")
        public long id;

        @SerializedName("title")
        public String title;

        @SerializedName("picurl")
        public String picUrl;

        @SerializedName("creator")
        public Creator creator;
    }

    public static class Creator {
        @SerializedName("nick")
        public String name;

        @SerializedName("encrypt_uin")
        public String uin;

        @SerializedName("headurl")
        public String headUrl;
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
    }

    public static class Album {
        @SerializedName("mid")
        public String mid;

        @SerializedName("name")
        public String name;

        @Nullable
        public String getPicUrl() {
            if (mid == null) return null;
            return String.format("https://y.qq.com/music/photo_new/T002R300x300M000%s.jpg", mid);
        }
    }

    public static class Singer {
        @SerializedName("name")
        public String name;
    }

    @Nullable
    public String getTitle() {
        if (playlistInfo == null || playlistInfo.data == null || playlistInfo.data.dirInfo == null) return null;
        return playlistInfo.data.dirInfo.title;
    }

    @Nullable
    public String getCreatorName() {
        if (playlistInfo == null || playlistInfo.data == null || playlistInfo.data.dirInfo == null || playlistInfo.data.dirInfo.creator == null) return null;
        return playlistInfo.data.dirInfo.creator.name;
    }

    @Nullable
    public String getCreatorUin() {
        if (playlistInfo == null || playlistInfo.data == null || playlistInfo.data.dirInfo == null || playlistInfo.data.dirInfo.creator == null) return null;
        return playlistInfo.data.dirInfo.creator.uin;
    }

    @Nullable
    public String getPicUrl() {
        if (playlistInfo == null || playlistInfo.data == null || playlistInfo.data.dirInfo == null) return null;
        return playlistInfo.data.dirInfo.picUrl;
    }

    @Nullable
    public List<TrackInfo> getSongs() {
        if (playlistInfo == null || playlistInfo.data == null) return null;
        return playlistInfo.data.songs;
    }
}