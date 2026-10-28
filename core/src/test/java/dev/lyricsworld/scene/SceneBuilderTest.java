package dev.lyricsworld.scene;

import dev.lyricsworld.config.Settings;
import dev.lyricsworld.emoji.EmojiAtlas;
import dev.lyricsworld.emoji.EmojiEngine;
import dev.lyricsworld.lyrics.LrcParser;
import dev.lyricsworld.lyrics.Lyrics;
import dev.lyricsworld.lyrics.LyricsSnapshot;
import dev.lyricsworld.emoji.EmojiEvent;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SceneBuilderTest {
    static final long MS = 1_000_000L;
    final EmojiAtlas atlas = EmojiAtlas.loadDefault();
    final EmojiEngine engine = EmojiEngine.loadDefault(atlas, null);
    final LyricsRenderer metrics = new LyricsRenderer() {
        @Override public int textWidth(String s) { return s.replace("\u00a7l", "").length() * 6; }
        @Override public int lineHeight() { return 9; }
        @Override public void draw(Scene scene) { }
    };
    SceneBuilder b;
    Settings s;
    PlayerView v;
    LyricsSnapshot snap;

    @Before
    public void setUp() {
        b = new SceneBuilder(metrics, atlas);
        s = new Settings().sanitize();
        v = new PlayerView();
        v.x = 10;
        v.y = 64;
        v.z = -5;
        v.yawDeg = 0;
        v.camYawDeg = 0;
        String lrc = "[00:10.00]<00:10.00>Hello <00:11.00>my <00:12.00>love <00:13.00>\n"
                + "[00:14.00]second line of text\n[00:18.00]third line\n[00:22.00]fourth line goes here\n[00:30.00]end";
        Lyrics l = LrcParser.parse(lrc, "t", true);
        snap = new LyricsSnapshot(l, engine.buildTimeline(l).toArray(new EmojiEvent[0]));
    }

    private Scene frame(long posMs) {
        return b.build(v, s, snap, posMs, true, posMs * MS);
    }

    @Test
    public void currentWordIsHighlightedSungDimmedUpcomingFaded() {
        s.fadeWhenMoving = false;
        Scene sc = frame(11_500);
        Scene.LineItem cur = null;
        for (int i = 0; i < sc.lineCount; i++) if (sc.lines[i].words[0].equals("Hello")) cur = sc.lines[i];
        assertNotNull(cur);
        assertEquals(3, cur.wordCount);
        int sung = cur.wordColor[0], now = cur.wordColor[1], next = cur.wordColor[2];
        assertEquals(s.colorSung & 0xFFFFFF, sung & 0xFFFFFF);
        assertTrue("current word is bigger", cur.wordScale[1] > 1f);
        assertEquals(1f, cur.wordScale[0], 0f);
        assertEquals(s.colorUpcoming & 0xFFFFFF, next & 0xFFFFFF);
        assertTrue("upcoming dimmer than the sung word being highlighted", (next >>> 24) < 255);
        assertNotEquals(sung & 0xFFFFFF, now & 0xFFFFFF);
    }

    @Test
    public void rowsRespectSettingAndAreBehindThePlayer() {
        s.fadeWhenMoving = false;
        s.rows = 3;
        Scene sc = frame(15_000);
        assertEquals(3, sc.lineCount);
        s.rows = 1;
        assertEquals(1, frame(15_000).lineCount);
        s.rows = 7;
        assertTrue(frame(15_000).lineCount >= 4);
        // yaw 0 faces +Z, so behind is -Z
        s.rows = 1;
        Scene.LineItem it = frame(15_000).lines[0];
        assertTrue(it.z < v.z);
        assertEquals(s.distance, Math.hypot(it.x - v.x, it.z - v.z), 0.01);
        assertEquals(v.y + s.height, it.y, 0.01);
    }

    @Test
    public void frontAndAbovePositions() {
        s.fadeWhenMoving = false;
        s.rows = 1;
        s.position = "FRONT";
        Scene.LineItem f = frame(15_000).lines[0];
        assertTrue(f.z > v.z);
        s.position = "ABOVE";
        Scene.LineItem a = frame(15_000).lines[0];
        assertEquals(v.x, a.x, 1e-9);
        assertEquals(v.z, a.z, 1e-9);
    }

    @Test
    public void followsPlayerRotation() {
        s.fadeWhenMoving = false;
        s.rows = 1;
        v.yawDeg = 90; // facing -X, behind is +X
        Scene.LineItem it = frame(15_000).lines[0];
        assertTrue(it.x > v.x);
    }

    @Test
    public void fadesWhenMovingAndRecovers() {
        s.rows = 1;
        s.emojiEnabled = false;
        Scene idle = frame(15_000);
        int idleAlpha = idle.lines[0].wordColor[0] >>> 24;
        v.moving = true;
        int a = 0;
        for (long t = 15_000; t < 17_500; t += 16) a = frame(t).lines[0].wordColor[0] >>> 24;
        assertTrue("alpha " + a + " < " + idleAlpha, a < idleAlpha);
        v.moving = false;
        int back = 0;
        for (long t = 17_500; t < 20_000; t += 16) {
            Scene sc = frame(t);
            if (sc.lineCount > 0) back = sc.lines[0].wordColor[0] >>> 24;
        }
        assertTrue(back > a);
    }

    @Test
    public void nothingBeforeLongIntroAndAfterLongGap() {
        s.fadeWhenMoving = false;
        assertEquals(0, frame(1_000).lineCount);
        // 4s+ after the last real line with no next line soon -> faded out
        Lyrics l = LrcParser.parse("[00:10.00]only line here\n[01:30.00]late line", "t", true);
        snap = new LyricsSnapshot(l, new EmojiEvent[0]);
        assertEquals(0, frame(40_000).lineCount);
    }

    @Test
    public void veryLongLinesAreShrunkToFit() {
        s.fadeWhenMoving = false;
        s.rows = 1;
        StringBuilder sb = new StringBuilder("[00:01.00]");
        for (int i = 0; i < 40; i++) sb.append("wonderful ");
        Lyrics l = LrcParser.parse(sb + "\n[00:20.00]x", "t", true);
        snap = new LyricsSnapshot(l, new EmojiEvent[0]);
        Scene.LineItem it = frame(2_000).lines[0];
        assertTrue(it.scale * it.widthPx <= 7.01f);
    }

    @Test
    public void sectionSignInLyricsCannotInjectFormatting() {
        Lyrics l = LrcParser.parse("[00:01.00]hello \u00a74red world\n[00:09.00]x", "t", true);
        snap = new LyricsSnapshot(l, new EmojiEvent[0]);
        s.fadeWhenMoving = false;
        Scene.LineItem it = frame(2_000).lines[0];
        for (int i = 0; i < it.wordCount; i++) assertFalse(it.words[i].indexOf('\u00a7') >= 0);
    }

    // ---- emoji ----

    private Lyrics emojiLyrics() {
        return LrcParser.parse("[00:01.00]you broke my heart\n[00:08.00]x", "t", true);
    }

    private void setEmojiSong() {
        Lyrics l = emojiLyrics();
        snap = new LyricsSnapshot(l, engine.buildTimeline(l).toArray(new EmojiEvent[0]));
        assertTrue(snap.events.length > 0);
    }

    private int emojiAfterPlaying(long from, long to) {
        int max = 0;
        for (long t = from; t < to; t += 16) max = Math.max(max, frame(t).emojiCount);
        return max;
    }

    @Test
    public void emojiSpawnAtFullIntensityAndAnimate() {
        setEmojiSong();
        s.emojiIntensity = 100;
        s.emojiCooldownMs = 0;
        s.fadeWhenMoving = false;
        long trigger = snap.events[0].timeMs;
        frame(trigger - 100);
        Scene sc = null;
        for (long t = trigger - 20; t < trigger + 100; t += 16) sc = frame(t);
        assertTrue(sc.emojiCount >= 1);
        float earlySize = sc.emoji[0].size;
        for (long t = trigger + 100; t < trigger + 600; t += 16) sc = frame(t);
        assertTrue("pop-in grows", sc.emoji[0].size >= earlySize * 0.9f);
        // rises
        double y0 = sc.emoji[0].y;
        for (long t = trigger + 600; t < trigger + 1500; t += 16) sc = frame(t);
        assertTrue(sc.emoji[0].y > y0);
        // gone after its life
        for (long t = trigger + 1500; t < trigger + 4500; t += 16) sc = frame(t);
        assertEquals(0, sc.emojiCount);
    }

    @Test
    public void zeroIntensityOrDisabledSpawnsNothing() {
        setEmojiSong();
        s.fadeWhenMoving = false;
        s.emojiIntensity = 0;
        assertEquals(0, emojiAfterPlaying(500, 4000));
        b.reset();
        s.emojiIntensity = 100;
        s.emojiEnabled = false;
        assertEquals(0, emojiAfterPlaying(500, 4000));
    }

    @Test
    public void disabledCategoryIsSkipped() {
        setEmojiSong();
        s.fadeWhenMoving = false;
        s.emojiIntensity = 100;
        s.emojiCooldownMs = 0;
        s.disabledEmojiCategories.add(snap.events[0].category);
        assertEquals(0, emojiAfterPlaying(500, 4000));
    }

    @Test
    public void seekingDoesNotReplaySkippedEvents() {
        setEmojiSong();
        s.fadeWhenMoving = false;
        s.emojiIntensity = 100;
        s.emojiCooldownMs = 0;
        frame(500);
        Scene sc = frame(7_000); // jump far past the event
        assertEquals(0, sc.emojiCount);
    }

    @Test
    public void mainOnlyFiltersNonMainEvents() {
        Lyrics l = LrcParser.parse("[00:01.00]the dog and the cat\n[00:08.00]x", "t", true);
        snap = new LyricsSnapshot(l, engine.buildTimeline(l).toArray(new EmojiEvent[0]));
        s.fadeWhenMoving = false;
        s.emojiIntensity = 100;
        s.emojiCooldownMs = 0;
        s.emojiMainOnly = true;
        for (EmojiEvent e : snap.events) if (e.main) fail("test sentence unexpectedly has a main event: " + e);
        assertEquals(0, emojiAfterPlaying(500, 5000));
    }

    @Test
    public void cooldownLimitsRate() {
        Lyrics l = LrcParser.parse("[00:01.00]love love love love love love love love\n[00:09.00]x", "t", true);
        snap = new LyricsSnapshot(l, engine.buildTimeline(l).toArray(new EmojiEvent[0]));
        s.fadeWhenMoving = false;
        s.emojiIntensity = 100;
        s.emojiCooldownMs = 5000;
        int spawnedFrames = 0;
        int last = 0;
        int spawns = 0;
        for (long t = 500; t < 9000; t += 16) {
            int c = frame(t).emojiCount;
            if (c > last) spawns += c - last;
            last = c;
        }
        assertTrue("spawns=" + spawns, spawns <= 3 + 1);
        assertTrue(spawnedFrames == 0);
    }

    @Test
    public void steadyStateFramesDoNotAllocate() {
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        if (!mx.isThreadAllocatedMemorySupported()) return;
        mx.setThreadAllocatedMemoryEnabled(true);
        for (String style : new String[]{"NORMAL", "BOLD_ITALIC"}) {
            s.fontStyle = style;
            s.emojiEnabled = true;
            s.emojiIntensity = 100;
            s.rows = 5;
            long t = 11_000;
            for (int i = 0; i < 400; i++) frame(t += 16); // warm up (JIT, layout cache, pools)
            long id = Thread.currentThread().getId();
            long before = mx.getThreadAllocatedBytes(id);
            for (int i = 0; i < 600; i++) frame(t += 16);
            long used = mx.getThreadAllocatedBytes(id) - before;
            // a handful of bytes per frame at most (boxing in the JDK, etc.), certainly not a per-word garbage stream
            assertTrue(style + ": allocated " + used + " bytes in 600 frames", used < 600 * 64);
        }
    }
}
