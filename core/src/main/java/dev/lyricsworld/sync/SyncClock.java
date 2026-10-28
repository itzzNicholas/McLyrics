package dev.lyricsworld.sync;

import dev.lyricsworld.media.PlaybackState;

/**
 * Turns sparse, jittery position samples (once per second or so) into a smooth, drift-free
 * playback position that can be queried every frame.
 *
 * <ul>
 *   <li>between samples the position advances with the monotonic clock;</li>
 *   <li>small disagreements (&lt; {@link #SEEK_THRESHOLD_MS}) are slewed away gradually so words never jump;</li>
 *   <li>large ones are treated as a seek and applied immediately;</li>
 *   <li>pause freezes the clock, a new track resets it.</li>
 * </ul>
 * Thread-safe: samples arrive on the media thread, queries on the render thread.
 */
public final class SyncClock {
    public static final long SEEK_THRESHOLD_MS = 450;
    private static final double SLEW = 0.25;

    private String trackKey = "";
    private boolean playing;
    private double baseMs;
    private long baseNanos;
    private long durationMs;
    private boolean hasTrack;

    public synchronized void onSample(PlaybackState s) {
        if (s == null || !s.hasTrack()) {
            hasTrack = false;
            trackKey = "";
            playing = false;
            return;
        }
        String key = s.trackKey();
        if (!hasTrack || !key.equals(trackKey)) {
            reset(s, key);
            return;
        }
        durationMs = s.durationMs;
        if (!s.playing) {
            baseMs = s.positionMs;
            baseNanos = s.sampledAtNanos;
            playing = false;
            return;
        }
        double predicted = playing ? baseMs + (s.sampledAtNanos - baseNanos) / 1e6 : -1e12;
        double error = s.positionMs - predicted;
        if (!playing || Math.abs(error) > SEEK_THRESHOLD_MS) {
            baseMs = s.positionMs;
            baseNanos = s.sampledAtNanos;
        } else {
            // advance base to the sample time, then nudge it
            baseMs = predicted + error * SLEW;
            baseNanos = s.sampledAtNanos;
        }
        playing = true;
    }

    private void reset(PlaybackState s, String key) {
        trackKey = key;
        hasTrack = true;
        playing = s.playing;
        baseMs = s.positionMs;
        baseNanos = s.sampledAtNanos;
        durationMs = s.durationMs;
    }

    /** Estimated playback position in ms at {@code nowNanos} (System.nanoTime based). */
    public synchronized long positionMs(long nowNanos) {
        if (!hasTrack) return 0;
        double p = playing ? baseMs + (nowNanos - baseNanos) / 1e6 : baseMs;
        if (durationMs > 0 && p > durationMs) p = durationMs;
        return p < 0 ? 0 : (long) p;
    }

    public synchronized boolean isPlaying() { return hasTrack && playing; }

    public synchronized boolean hasTrack() { return hasTrack; }

    public synchronized String trackKey() { return trackKey; }
}
