package dev.lyricsworld.lyrics;

import dev.lyricsworld.emoji.EmojiEvent;

/** Lyrics of the current track together with the emoji events precomputed from them. Immutable. */
public final class LyricsSnapshot {
    public final Lyrics lyrics;
    public final EmojiEvent[] events;

    public LyricsSnapshot(Lyrics lyrics, EmojiEvent[] events) {
        this.lyrics = lyrics;
        this.events = events;
    }

    /** Index of the first event with {@code timeMs >= posMs}. */
    public int firstEventAtOrAfter(long posMs) {
        int lo = 0, hi = events.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (events[mid].timeMs < posMs) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }
}
