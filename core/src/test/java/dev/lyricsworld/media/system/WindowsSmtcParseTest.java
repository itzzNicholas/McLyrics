package dev.lyricsworld.media.system;

import dev.lyricsworld.media.PlaybackState;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class WindowsSmtcParseTest {
    @Test
    public void parsesArrayAndSingleObject() {
        String arr = "[{\"app\":\"Spotify.exe\",\"title\":\"A\",\"artist\":\"X\",\"album\":\"\",\"pos\":1000,\"dur\":200000,\"playing\":true},"
                + "{\"app\":\"chrome.exe\",\"title\":\"B\",\"artist\":\"Y\",\"album\":\"\",\"pos\":5,\"dur\":100,\"playing\":false}]";
        List<Candidate> l = WindowsSmtcSource.parse(arr, 0);
        assertEquals(2, l.size());
        assertEquals("A", Candidate.pick(l, null, "Windows").title);
        assertEquals("B", Candidate.pick(l, "chrome", "Windows").title);
        assertNull(Candidate.pick(l, "apple", "Windows"));
        assertEquals(1, WindowsSmtcSource.parse("{\"app\":\"a\",\"title\":\"t\"}", 0).size());
        assertEquals(0, WindowsSmtcSource.parse("[]", 0).size());
    }

    @Test
    public void prefersPlayingSession() {
        List<Candidate> l = WindowsSmtcSource.parse("[{\"app\":\"a\",\"title\":\"paused\",\"playing\":false},"
                + "{\"app\":\"b\",\"title\":\"playing\",\"playing\":true}]", 0);
        PlaybackState s = Candidate.pick(l, null, "Windows");
        assertEquals("playing", s.title);
        assertTrue(s.playing);
    }

    @Test
    public void emptyTitlesAreIgnored() {
        assertNull(Candidate.pick(WindowsSmtcSource.parse("[{\"app\":\"a\",\"title\":\"\",\"playing\":true}]", 0), null, "W"));
    }
}
