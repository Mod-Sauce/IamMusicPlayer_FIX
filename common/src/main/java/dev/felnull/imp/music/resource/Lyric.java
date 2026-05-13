package dev.felnull.imp.music.resource;

import it.unimi.dsi.fastutil.floats.Float2ObjectSortedMap;
import org.jetbrains.annotations.Nullable;
import oshi.util.tuples.Pair;

public class Lyric {
    private final Float2ObjectSortedMap<String> lyrics;
    @Nullable
    private final Float2ObjectSortedMap<String> transLyrics;
    public static final Lyric EMPTY = new Lyric();

    public Lyric(Float2ObjectSortedMap<String> lyrics, @Nullable Float2ObjectSortedMap<String> transLyrics){
        this.lyrics = lyrics;
        this.transLyrics = transLyrics;
    }

    public Lyric(Float2ObjectSortedMap<String> lyrics){
        this(lyrics, null);
    }

    public Lyric(){
        this(null, null);
    }

    public boolean isEmpty(){
        return lyrics == null;
    }

    @SuppressWarnings("all")
    public Pair<String, String> getPart(float second){
        var keyList = lyrics.keySet().stream().toList();
        var valueList = lyrics.values().stream().toList();
        var text = "";
        var lyricTime = 0f;
        String transformText = null;
        for (int i = 0; i < lyrics.size(); i++) {
            if (i == lyrics.size() - 1) {
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
        }
        return new Pair(text, transformText);
    }

    public boolean hasTrans(){
        return transLyrics != null && !transLyrics.isEmpty();
    }
}
