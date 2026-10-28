package dev.lyricsworld.lyrics;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class LyricsCacheTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void roundTripAndNegativeEntries() throws Exception {
        LyricsCache c = new LyricsCache(tmp.newFolder().toPath());
        assertNull(c.get("a"));
        c.putFound("a", "[00:01.00]hi", "LRCLIB");
        LyricsCache.Entry e = c.get("a");
        assertTrue(e.found);
        assertEquals("[00:01.00]hi", e.lrc);
        c.putMiss("b");
        assertFalse(c.get("b").found);
    }

    @Test
    public void longKeysAreSafe() throws Exception {
        LyricsCache c = new LyricsCache(tmp.newFolder().toPath());
        StringBuilder k = new StringBuilder();
        for (int i = 0; i < 400; i++) k.append('x');
        c.putFound(k.toString(), "l", "s");
        assertTrue(c.get(k.toString()).found);
    }
}
