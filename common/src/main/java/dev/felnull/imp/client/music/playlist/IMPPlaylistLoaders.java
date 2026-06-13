package dev.felnull.imp.client.music.playlist;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class IMPPlaylistLoaders {
    private static final Map<String, IPlaylistLoader> LOADERS = new HashMap<>();

    public static void registry(IPlaylistLoader loader){
        LOADERS.put(loader.getID(), loader);
    }

    public static Collection<IPlaylistLoader> getAllLoaders(){
        return LOADERS.values();
    }

    public static IPlaylistLoader getLoader(String id){
        return LOADERS.get(id);
    }

    public static void init(){
        registry(new NeteasePlaylistLoader());
        registry(new YoutubePlayListLoader());
        registry(new BilibiliPlaylistLoader());
    }
}
