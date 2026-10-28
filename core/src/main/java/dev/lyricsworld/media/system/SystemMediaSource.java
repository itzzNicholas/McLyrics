package dev.lyricsworld.media.system;

import dev.lyricsworld.media.MediaSource;
import dev.lyricsworld.media.PlaybackState;

/**
 * OS media-session fallback: Windows SMTC, macOS scriptable players, Linux MPRIS (playerctl).
 * Use {@link #create(String, String)} to get the implementation for the running OS.
 */
public abstract class SystemMediaSource implements MediaSource {
    protected volatile String filter;
    protected final String label;
    protected volatile String error;

    protected SystemMediaSource(String label, String filter) {
        this.label = label;
        this.filter = filter;
    }

    /**
     * @param label  name shown in the UI
     * @param filter case-insensitive substring of the player/app name to accept, or null for any player
     */
    public static SystemMediaSource create(String label, String filter) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return new WindowsSmtcSource(label, filter);
        if (os.contains("mac") || os.contains("darwin")) return new MacScriptSource(label, filter);
        return new LinuxMprisSource(label, filter);
    }

    /** Restrict to players whose name contains this text (null/empty = any). Can change at runtime. */
    public void setFilter(String filter) { this.filter = filter; }

    @Override public String name() { return label; }

    @Override public String lastError() { return error; }

    @Override public abstract PlaybackState poll();
}
