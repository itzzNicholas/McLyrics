package dev.lyricsworld.lyrics;

import org.junit.Test;

import static org.junit.Assert.*;

public class LrcParserTest {
    @Test
    public void parsesPlainLinesAndEstimatesWords() {
        String lrc = "[ar:Someone]\n[00:10.00]Hello dark world\n[00:14.50]Second line here\n";
        Lyrics l = LrcParser.parse(lrc, "t", true);
        assertEquals(2, l.lines.size());
        Lyrics.Line a = l.lines.get(0);
        assertEquals(10_000, a.startMs);
        assertEquals(3, a.words.length);
        assertTrue(a.estimated);
        assertEquals("Hello", a.words[0].text);
        assertEquals(10_000, a.words[0].startMs);
        // words are contiguous and increasing
        for (int i = 1; i < a.words.length; i++) assertEquals(a.words[i - 1].endMs, a.words[i].startMs);
        assertTrue(a.words[2].endMs <= 14_500);
        assertTrue(l.hasEstimatedLines());
    }

    @Test
    public void longerWordsGetMoreTime() {
        Lyrics l = LrcParser.parse("[00:00.00]a extraordinarily\n[00:10.00]x", "t", true);
        Lyrics.Word shortW = l.lines.get(0).words[0];
        Lyrics.Word longW = l.lines.get(0).words[1];
        assertTrue(longW.endMs - longW.startMs > shortW.endMs - shortW.startMs);
    }

    @Test
    public void parsesEnhancedLrcWordTimestamps() {
        String lrc = "[00:12.00]<00:12.00>Hello <00:12.50>big <00:13.00>world <00:13.80>\n[00:15.00]next\n";
        Lyrics l = LrcParser.parse(lrc, "t", true);
        Lyrics.Line a = l.lines.get(0);
        assertFalse(a.estimated);
        assertEquals(3, a.words.length);
        assertEquals(12_000, a.words[0].startMs);
        assertEquals(12_500, a.words[0].endMs);
        assertEquals(12_500, a.words[1].startMs);
        assertEquals("world", a.words[2].text);
        assertEquals(13_000, a.words[2].startMs);
        assertEquals(13_800, a.words[2].endMs);
    }

    @Test
    public void enhancedSegmentWithSeveralWordsIsSplit() {
        Lyrics l = LrcParser.parse("[00:01.00]<00:01.00>one two three<00:02.00>\n[00:05.00]x", "t", true);
        Lyrics.Line a = l.lines.get(0);
        assertEquals(3, a.words.length);
        assertEquals(1000, a.words[0].startMs);
        assertEquals(2000, a.words[2].endMs);
    }

    @Test
    public void multipleTimestampsRepeatTheLine() {
        Lyrics l = LrcParser.parse("[00:10.00][00:30.00]Chorus\n[00:20.00]Verse", "t", true);
        assertEquals(3, l.lines.size());
        assertEquals("Chorus", l.lines.get(0).text);
        assertEquals("Verse", l.lines.get(1).text);
        assertEquals("Chorus", l.lines.get(2).text);
        assertEquals(30_000, l.lines.get(2).startMs);
    }

    @Test
    public void appliesOffsetTag() {
        // positive offset = lyrics should appear earlier
        Lyrics l = LrcParser.parse("[offset:+500]\n[00:10.00]Hi there\n[00:20.00]x", "t", true);
        assertEquals(9_500, l.lines.get(0).startMs);
    }

    @Test
    public void blankLinesAreKeptAsInstrumentalMarkers() {
        Lyrics l = LrcParser.parse("[00:01.00]Words here\n[00:05.00]\n[00:09.00]More words", "t", true);
        assertEquals(3, l.lines.size());
        assertTrue(l.lines.get(1).isBlank());
        assertTrue(l.hasText());
    }

    @Test
    public void threeDigitFractionAndColonSeparator() {
        Lyrics l = LrcParser.parse("[01:02.345]a b\n[01:09:50]c d", "t", true);
        assertEquals(62_345, l.lines.get(0).startMs);
        assertEquals(69_500, l.lines.get(1).startMs);
    }

    @Test
    public void garbageDoesNotThrow() {
        assertFalse(LrcParser.parse(null, "t", true).hasText());
        assertFalse(LrcParser.parse("", "t", true).hasText());
        assertFalse(LrcParser.parse("just plain lyrics\nwithout stamps", "t", true).hasText());
        assertFalse(LrcParser.parse("[xx:yy]nonsense\n[99:99.99", "t", true).hasText());
    }

    @Test
    public void estimationDisabledKeepsLineAsOneUnit() {
        Lyrics l = LrcParser.parse("[00:01.00]one two three\n[00:09.00]x", "t", false);
        assertEquals(1, l.lines.get(0).words.length);
        assertEquals("one two three", l.lines.get(0).words[0].text);
    }

    @Test
    public void lineIndexAtBinarySearch() {
        Lyrics l = LrcParser.parse("[00:05.00]a\n[00:10.00]b\n[00:20.00]c", "t", true);
        assertEquals(-1, l.lineIndexAt(1000));
        assertEquals(0, l.lineIndexAt(5000));
        assertEquals(0, l.lineIndexAt(9999));
        assertEquals(1, l.lineIndexAt(10_000));
        assertEquals(2, l.lineIndexAt(999_999));
    }

    @Test
    public void lastLineIsBoundedEvenWithoutNextLine() {
        Lyrics l = LrcParser.parse("[00:05.00]only line", "t", true);
        Lyrics.Line a = l.lines.get(0);
        assertTrue(a.endMs - a.startMs <= LrcParser.MAX_LINE_MS);
    }
}
