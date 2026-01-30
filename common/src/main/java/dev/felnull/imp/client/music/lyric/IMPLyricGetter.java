package dev.felnull.imp.client.music.lyric;

import dev.felnull.imp.music.resource.MusicSource;

import java.util.HashMap;
import java.util.Map;

public class IMPLyricGetter {
    private static final Map<String, LyricGetterType> ALL_GETTER = new HashMap<>();

    public static void register(LyricGetterType getterType){
        ALL_GETTER.put(getterType.getLoaderType(), getterType);
    }

    public static LyricGetter getGetter(MusicSource source){
        if(!ALL_GETTER.containsKey(source.getLoaderType()))return null;
        return ALL_GETTER.get(source.getLoaderType()).getLyricGetter();
    }

    public static void init(){
        register(new LyricGetterType() {
            @Override
            public String getLoaderType() {
                return "netease";
            }

            @Override
            public LyricGetter getLyricGetter() {
                return new NetEaseLyricGetter();
            }
        });
    }
}
