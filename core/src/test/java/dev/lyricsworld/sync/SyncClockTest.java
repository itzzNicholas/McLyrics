package dev.lyricsworld.sync;

import dev.lyricsworld.media.PlaybackState;
import org.junit.Test;

import static org.junit.Assert.*;

public class SyncClockTest {
    private static final long MS = 1_000_000L;

    private static PlaybackState st(String title, long pos, boolean playing, long atNanos) {
        return new PlaybackState(title, "Artist", "", 200_000, pos, playing, atNanos, "test");
    }

    @Test
    public void advancesWithTheClockWhilePlaying() {
        SyncClock c = new SyncClock();
        c.onSample(st("A", 10_000, true, 0));
        assertEquals(10_000, c.positionMs(0));
        assertEquals(12_500, c.positionMs(2500 * MS));
    }

    @Test
    public void pauseFreezesPosition() {
        SyncClock c = new SyncClock();
        c.onSample(st("A", 10_000, true, 0));
        c.onSample(st("A", 11_000, false, 1000 * MS));
        assertEquals(11_000, c.positionMs(5000 * MS));
        assertFalse(c.isPlaying());
        c.onSample(st("A", 11_000, true, 6000 * MS));
        assertEquals(12_000, c.positionMs(7000 * MS));
    }

    @Test
    public void smallDriftIsSlewedNotSnapped() {
        SyncClock c = new SyncClock();
        c.onSample(st("A", 10_000, true, 0));
        // source says we are 200ms ahead of our prediction after 1s
        c.onSample(st("A", 11_200, true, 1000 * MS));
        long p = c.positionMs(1000 * MS);
        assertTrue("moved toward the sample but not all the way: " + p, p > 11_000 && p < 11_200);
        // repeated agreeing samples converge
        for (int i = 2; i < 30; i++) c.onSample(st("A", 10_000 + i * 1000 + 200, true, i * 1000 * MS));
        assertEquals(10_000 + 29 * 1000 + 200, c.positionMs(29_000 * MS), 20);
    }

    @Test
    public void bigJumpIsASeek() {
        SyncClock c = new SyncClock();
        c.onSample(st("A", 10_000, true, 0));
        c.onSample(st("A", 90_000, true, 1000 * MS));
        assertEquals(90_000, c.positionMs(1000 * MS));
    }

    @Test
    public void trackChangeResets() {
        SyncClock c = new SyncClock();
        c.onSample(st("A", 100_000, true, 0));
        c.onSample(st("B", 500, true, 1000 * MS));
        assertEquals(500, c.positionMs(1000 * MS));
        assertEquals(c.trackKey(), st("B", 0, true, 0).trackKey());
    }

    @Test
    public void nothingPlayingAfterNullSample() {
        SyncClock c = new SyncClock();
        c.onSample(st("A", 100, true, 0));
        c.onSample(null);
        assertFalse(c.hasTrack());
        assertEquals(0, c.positionMs(5000 * MS));
    }

    @Test
    public void positionNeverExceedsDuration() {
        SyncClock c = new SyncClock();
        c.onSample(st("A", 199_000, true, 0));
        assertEquals(200_000, c.positionMs(60_000 * MS));
    }
}
