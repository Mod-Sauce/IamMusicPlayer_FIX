package dev.felnull.imp.client.cache;

import java.util.ArrayList;
import java.util.List;

public class LyricDTO {

    public List<LyricEntry> lyrics = new ArrayList<>();
    public List<LyricEntry> transLyrics = new ArrayList<>();

    public static class LyricEntry {
        public float time;
        public String text;

        public LyricEntry() {}

        public LyricEntry(float time, String text) {
            this.time = time;
            this.text = text;
        }
    }
}