package dev.felnull.imp.client.cache;

import java.util.ArrayList;
import java.util.List;

public class LyricDTO {

    public List<LyricEntry> lyrics = new ArrayList<>();
    public List<LyricEntry> transLyrics = new ArrayList<>();
    /** Per-character data (only kugou krc has it); null when the cache lacks the field. */
    public List<WordLine> wordLyrics = new ArrayList<>();

    public static class LyricEntry {
        public float time;
        public String text;

        public LyricEntry() {}

        public LyricEntry(float time, String text) {
            this.time = time;
            this.text = text;
        }
    }

    /** One per-character line: line start seconds + word list of that line. */
    public static class WordLine {
        public float time;
        public List<Word> words = new ArrayList<>();
    }

    /** One word: text + absolute start and end (seconds). */
    public static class Word {
        public String text;
        public float start;
        public float duration;

        public Word() {}

        public Word(String text, float start, float duration) {
            this.text = text;
            this.start = start;
            this.duration = duration;
        }
    }
}