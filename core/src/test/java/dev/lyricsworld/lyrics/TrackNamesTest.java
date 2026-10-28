package dev.lyricsworld.lyrics;

import org.junit.Test;

import static org.junit.Assert.*;

public class TrackNamesTest {
    @Test
    public void cleansTitles() {
        assertEquals("Song", TrackNames.cleanTitle("Song (feat. Someone)"));
        assertEquals("Song", TrackNames.cleanTitle("Song - 2011 Remaster"));
        assertEquals("Song", TrackNames.cleanTitle("Song [Deluxe Edition]"));
        assertEquals("Hello (I Love You)", TrackNames.cleanTitle("Hello (I Love You)"));
    }

    @Test
    public void firstArtistOnly() {
        assertEquals("Mina", TrackNames.firstArtist("Mina, Celentano"));
        assertEquals("Daft Punk", TrackNames.firstArtist("Daft Punk & Pharrell"));
    }

    @Test
    public void keyIsStableAcrossFormatting() {
        assertEquals(TrackNames.key("Björk", "Jóga", 304_000), TrackNames.key("bjork", "joga", 304_500));
    }
}
