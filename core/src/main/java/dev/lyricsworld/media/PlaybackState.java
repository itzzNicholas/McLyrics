package dev.lyricsworld.media;

/** Immutable snapshot of what the user's player was doing at {@link #sampledAtNanos}. */
public final class PlaybackState {
    public final String title;
    public final String artist;
    public final String album;
    public final long durationMs;
    /** Position at the moment the sample was taken (already latency-compensated by the source). */
    public final long positionMs;
    public final boolean playing;
    public final long sampledAtNanos;
    public final String sourceName;

    public PlaybackState(String title, String artist, String album, long durationMs,
                         long positionMs, boolean playing, long sampledAtNanos, String sourceName) {
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.album = album == null ? "" : album;
        this.durationMs = Math.max(0, durationMs);
        this.positionMs = Math.max(0, positionMs);
        this.playing = playing;
        this.sampledAtNanos = sampledAtNanos;
        this.sourceName = sourceName == null ? "" : sourceName;
    }

    public boolean hasTrack() { return !title.isEmpty(); }

    /** Identity of the track, used to detect track changes. */
    public String trackKey() {
        return artist.toLowerCase() + "\u0000" + title.toLowerCase() + "\u0000" + (durationMs / 1000);
    }

    @Override
    public String toString() {
        return sourceName + ": " + artist + " - " + title + " @" + positionMs + "/" + durationMs
                + (playing ? " ▶" : " ⏸");
    }
}
