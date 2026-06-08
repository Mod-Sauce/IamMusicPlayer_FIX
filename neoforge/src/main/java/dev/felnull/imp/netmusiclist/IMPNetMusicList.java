package dev.felnull.imp.netmusiclist;

import dev.felnull.imp.client.music.playlist.IMPPlaylistLoaders;

public class IMPNetMusicList {
    public static void init(){
        IMPPlaylistLoaders.registry(new NetMusicPlayListLoader());
    }
}
