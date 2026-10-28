package dev.lyricsworld.media;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class SpotifyTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void pkceChallengeMatchesRfc7636Vector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                SpotifyAuth.sha256Base64Url("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
    }

    @Test
    public void parsesCurrentlyPlaying() {
        String body = "{\"is_playing\":true,\"progress_ms\":42000,\"currently_playing_type\":\"track\",\"item\":{"
                + "\"name\":\"Song\",\"duration_ms\":210000,\"artists\":[{\"name\":\"Artist\"},{\"name\":\"B\"}],"
                + "\"album\":{\"name\":\"Alb\"}}}";
        PlaybackState s = SpotifyWebApiSource.parse(body, 5);
        assertEquals("Song", s.title);
        assertEquals("Artist", s.artist);
        assertEquals("Alb", s.album);
        assertEquals(42000, s.positionMs);
        assertEquals(210000, s.durationMs);
        assertTrue(s.playing);
    }

    @Test
    public void adsAndEpisodesAreIgnored() {
        assertNull(SpotifyWebApiSource.parse("{\"currently_playing_type\":\"ad\",\"item\":null}", 0));
        assertNull(SpotifyWebApiSource.parse("{\"currently_playing_type\":\"episode\",\"item\":{\"name\":\"pod\"}}", 0));
    }

    @Test
    public void tokenStoreEncryptsAtRest() throws Exception {
        java.nio.file.Path f = tmp.newFolder().toPath().resolve("tok.bin");
        SpotifyTokenStore st = new SpotifyTokenStore(f);
        assertNull(st.load());
        st.save("super-secret-refresh-token");
        byte[] raw = java.nio.file.Files.readAllBytes(f);
        assertFalse(new String(raw, "ISO-8859-1").contains("super-secret"));
        assertEquals("super-secret-refresh-token", new SpotifyTokenStore(f).load());
        st.clear();
        assertNull(st.load());
    }

    @Test
    public void tamperedTokenFileIsRejected() throws Exception {
        java.nio.file.Path f = tmp.newFolder().toPath().resolve("tok.bin");
        SpotifyTokenStore st = new SpotifyTokenStore(f);
        st.save("abc");
        byte[] raw = java.nio.file.Files.readAllBytes(f);
        raw[raw.length - 1] ^= 1;
        java.nio.file.Files.write(f, raw);
        assertNull(st.load());
    }

    @Test
    public void trackKeyIgnoresCase() {
        PlaybackState a = new PlaybackState("Song", "ARTIST", "", 100_000, 0, true, 0, "x");
        PlaybackState b = new PlaybackState("song", "artist", "", 100_400, 0, true, 0, "x");
        assertEquals(a.trackKey(), b.trackKey());
    }
}
