package dev.lyricsworld.media;

/** A way of finding out what the user's music player is doing. Implementations must never throw. */
public interface MediaSource {
    /** Human readable name, e.g. "Spotify Web API". */
    String name();

    /** Whether this source can work at all right now (credentials present, OS supported...). */
    boolean isAvailable();

    /**
     * Blocking poll (called from the media thread, never from the game thread).
     * @return the current state, or null when nothing is playing / the player is closed.
     */
    PlaybackState poll();

    /** Last human readable problem, or null. Shown in the external window. */
    String lastError();

    /** Release resources (child processes...). */
    void close();
}
