package dev.lyricsworld.media.system;

import dev.lyricsworld.media.PlaybackState;

import java.util.List;

/** One player/session reported by the OS. */
final class Candidate {
    final String app;
    final String title;
    final String artist;
    final String album;
    final long durationMs;
    final long positionMs;
    final boolean playing;
    final long sampledAtNanos;

    Candidate(String app, String title, String artist, String album, long durationMs,
              long positionMs, boolean playing, long sampledAtNanos) {
        this.app = app == null ? "" : app;
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.album = album == null ? "" : album;
        this.durationMs = durationMs;
        this.positionMs = positionMs;
        this.playing = playing;
        this.sampledAtNanos = sampledAtNanos;
    }

    /**
     * Picks the best candidate: must match {@code filter} (case-insensitive substring of the app name,
     * null/empty = any), prefers a playing one, ignores sessions without a title.
     */
    static PlaybackState pick(List<Candidate> all, String filter, String sourcePrefix) {
        Candidate best = null;
        for (Candidate c : all) {
            if (c.title.isEmpty()) continue;
            if (filter != null && !filter.isEmpty() && !c.app.toLowerCase().contains(filter.toLowerCase())) continue;
            if (best == null || (c.playing && !best.playing)) best = c;
        }
        if (best == null) return null;
        return new PlaybackState(best.title, best.artist, best.album, best.durationMs,
                best.positionMs, best.playing, best.sampledAtNanos,
                sourcePrefix + (best.app.isEmpty() ? "" : " (" + best.app + ")"));
    }
}
