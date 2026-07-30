package dev.felnull.imp.music.resource;

import dev.felnull.imp.client.cache.LyricDTO;
import it.unimi.dsi.fastutil.floats.Float2ObjectLinkedOpenHashMap;
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
        if(isEmpty())return new Pair<>("", "");
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

    public static LyricDTO toDTO(Lyric lyric) {
        if (lyric == null || lyric.isEmpty()) return null;

        LyricDTO dto = new LyricDTO();

        lyric.lyrics.forEach((time, text) -> {
            dto.lyrics.add(new LyricDTO.LyricEntry(time, text));
        });

        if (lyric.transLyrics != null) {
            lyric.transLyrics.forEach((time, text) -> {
                dto.transLyrics.add(new LyricDTO.LyricEntry(time, text));
            });
        }

        return dto;
    }

    public static Lyric fromDTO(LyricDTO dto) {
        if (dto == null) return Lyric.EMPTY;

        Float2ObjectSortedMap<String> lyrics = new Float2ObjectLinkedOpenHashMap<>();
        Float2ObjectSortedMap<String> trans = new Float2ObjectLinkedOpenHashMap<>();

        if (dto.lyrics != null) {
            for (LyricDTO.LyricEntry e : dto.lyrics) {
                lyrics.put(e.time, e.text);
            }
        }

        if (dto.transLyrics != null) {
            for (LyricDTO.LyricEntry e : dto.transLyrics) {
                trans.put(e.time, e.text);
            }
        }

        return new Lyric(lyrics, trans.isEmpty() ? null : trans);
    }
}
