package dev.lyricsworld.lyrics;

import org.junit.Test;

import static org.junit.Assert.*;

public class LrcLibClientTest {
    @Test
    public void picksClosestDurationWithSyncedLyrics() {
        String body = "[{\"duration\":200,\"syncedLyrics\":\"[00:01.00]far\"},"
                + "{\"duration\":181,\"syncedLyrics\":\"[00:01.00]close\"},"
                + "{\"duration\":180,\"syncedLyrics\":null,\"plainLyrics\":\"x\"}]";
        assertEquals("[00:01.00]close", LrcLibClient.pickBest(body, 180_000));
    }

    @Test
    public void rejectsDifferentRecordings() {
        String body = "[{\"duration\":260,\"syncedLyrics\":\"[00:01.00]other\"}]";
        assertNull(LrcLibClient.pickBest(body, 180_000));
    }

    @Test
    public void skipsInstrumentals() {
        String body = "[{\"duration\":180,\"instrumental\":true,\"syncedLyrics\":\"[00:01.00]x\"}]";
        assertNull(LrcLibClient.pickBest(body, 180_000));
    }
}
