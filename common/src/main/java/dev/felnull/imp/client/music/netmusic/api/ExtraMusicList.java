package dev.felnull.imp.client.music.netmusic.api;

import com.google.common.collect.Lists;
import com.google.gson.annotations.SerializedName;
import dev.felnull.imp.client.music.netmusic.api.pojo.NetEaseMusicList;

import java.util.List;

public class ExtraMusicList {
    @SerializedName("code")
    private int code;

    @SerializedName("songs")
    private List<NetEaseMusicList.Track> tracks = Lists.newArrayList();

    public int getCode() {
        return code;
    }

    public List<NetEaseMusicList.Track> getTracks() {
        return tracks;
    }
}
