package dev.lyricsworld.lyrics;

import java.util.Collections;
import java.util.List;

/** Parsed, time-aligned lyrics of one track. Immutable after construction. */
public final class Lyrics {
    public static final class Word {
        public final String text;
        public final long startMs;
        public final long endMs;

        public Word(String text, long startMs, long endMs) {
            this.text = text;
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    public static final class Line {
        public final long startMs;
        public final long endMs;
        public final Word[] words;
        /** True when the word timings were interpolated instead of coming from the source. */
        public final boolean estimated;
        public final String text;

        public Line(long startMs, long endMs, Word[] words, boolean estimated) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.words = words;
            this.estimated = estimated;
            StringBuilder sb = new StringBuilder();
            for (Word w : words) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(w.text);
            }
            this.text = sb.toString();
        }

        public boolean isBlank() { return words.length == 0; }
    }

    public final List<Line> lines;
    public final String source;

    public Lyrics(List<Line> lines, String source) {
        this.lines = Collections.unmodifiableList(lines);
        this.source = source;
    }

    /** True if at least one line has interpolated (not source-provided) word timings. */
    public boolean hasEstimatedLines() {
        for (Line l : lines) if (l.estimated && !l.isBlank()) return true;
        return false;
    }

    public boolean hasText() {
        for (Line l : lines) if (!l.isBlank()) return true;
        return false;
    }

    /** Index of the last line starting at or before {@code posMs}, or -1 before the first line. */
    public int lineIndexAt(long posMs) {
        int lo = 0, hi = lines.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).startMs <= posMs) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }
}
