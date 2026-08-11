package dev.felnull.imp.client.music.netmusic.api.pojo;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

public class NetEaseMusicSearch {

    @SerializedName("code")
    private int code;

    @SerializedName("result")
    private Result result;

    public static class Result {

        @SerializedName("songs")
        private List<Song> songs;
    }

    public static class Song {

        @SerializedName("id")
        private long id;

        public long getId() {
            return id;
        }
    }

    public List<Song> getSongs() {
        if (result == null || result.songs == null) {
            return Collections.emptyList();
        }
        return result.songs;
    }

    public int getCode() {
        return code;
    }
}