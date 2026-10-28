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
    final GlyphAtlas glyphs = GlyphAtlas.loadDefault();
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
        b = new SceneBuilder(metrics, atlas, glyphs);
        s = new Settings().sanitize();
        // most tests below exercise the classic player-relative layout with the game font
        s.position = "BEHIND";
        s.fontMode = "GAME";
        s.fadeWhenMoving = true;
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
        for (String style : new String[]{"NORMAL", "BOLD_ITALIC", "ROCK-VIEW"}) {
            if (style.equals("ROCK-VIEW")) {
                s.fontMode = "ROCK";
                s.position = "VIEW";
            } else {
                s.fontStyle = style;
            }
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

    // ---- new default layout: in front of the view, centre-low, scattered left/right, Rock font ----

    private void viewMode() {
        s.position = "VIEW";
        s.fontMode = "ROCK";
        s.fadeWhenMoving = false;
        s.rows = 3;
    }

    private double lateralOf(Scene.LineItem it) {
        double rightX = -Math.cos(Math.toRadians(v.camYawDeg)), rightZ = -Math.sin(Math.toRadians(v.camYawDeg));
        return (it.x - v.camX) * rightX + (it.z - v.camZ) * rightZ;
    }

    @Test
    public void viewModeIsInFrontOfTheCameraAndBelowCentre() {
        viewMode();
        v.camX = v.x;
        v.camY = v.y + 1.62;
        v.camZ = v.z;
        v.camYawDeg = 0; // facing +Z
        v.camPitchDeg = 0;
        Scene sc = frame(15_000);
        assertTrue(sc.customFont);
        assertTrue(sc.lineCount >= 2);
        for (int i = 0; i < sc.lineCount; i++) {
            Scene.LineItem it = sc.lines[i];
            assertEquals("distance in front", s.distance, it.z - v.camZ, 0.01);
            assertTrue("below the view centre", it.y < v.camY);
        }
        assertFalse("depth-tested by default (blends with entities)", sc.seeThrough);
    }

    @Test
    public void viewModeFollowsWhereYouLook() {
        viewMode();
        v.camX = 0;
        v.camY = 65.62;
        v.camZ = 0;
        v.camYawDeg = 90; // facing -X
        Scene.LineItem west = frame(15_000).lines[0];
        assertTrue(west.x < -2.5);
        v.camYawDeg = 0;
        v.camPitchDeg = -60; // looking up
        Scene.LineItem up = frame(15_100).lines[0];
        assertTrue("looking up lifts the text", up.y > v.camY);
    }

    @Test
    public void viewModeScattersLinesLeftAndRight() {
        viewMode();
        s.rows = 5;
        v.camYawDeg = 0;
        Scene sc = frame(15_000);
        int left = 0, right = 0;
        for (int i = 0; i < sc.lineCount; i++) {
            double lat = lateralOf(sc.lines[i]);
            if (lat < -0.1) left++;
            if (lat > 0.1) right++;
        }
        assertTrue("left=" + left + " right=" + right, left >= 1 && right >= 1);
        // lines are tilted, alternating
        boolean tilted = false;
        for (int i = 0; i < sc.lineCount; i++) tilted |= Math.abs(sc.lines[i].rollDeg) > 0.5f;
        assertTrue(tilted);
    }

    @Test
    public void emojiScatterToBothSidesInViewMode() {
        viewMode();
        StringBuilder lrc = new StringBuilder();
        for (int i = 0; i < 30; i++) lrc.append(String.format("[00:%02d.00]my love and my heart\n", i + 1));
        Lyrics l = LrcParser.parse(lrc + "[01:00.00]end", "t", true);
        snap = new LyricsSnapshot(l, engine.buildTimeline(l).toArray(new EmojiEvent[0]));
        s.emojiIntensity = 100;
        s.emojiCooldownMs = 0;
        int left = 0, right = 0;
        for (long t = 500; t < 30_000; t += 40) {
            Scene sc = frame(t);
            for (int i = 0; i < sc.emojiCount; i++) {
                double rightX = -1, rightZ = 0; // camYaw 0
                double lat = (sc.emoji[i].x - v.camX) * rightX + (sc.emoji[i].z - v.camZ) * rightZ;
                if (lat < -0.2) left++;
                if (lat > 0.2) right++;
            }
        }
        assertTrue("left=" + left + " right=" + right, left > 5 && right > 5);
    }

    @Test
    public void rockFontGlyphsAreEmittedAsOutlineAndFillQuads() {
        assertTrue(glyphs.isLoaded());
        assertTrue(glyphs.width("Hello") > glyphs.width("He"));
        for (char c : "àèéìòùÀÈÉ’!?".toCharArray()) assertTrue("glyph for " + c, glyphs.advance(c) > 0);
        Scene.LineItem it = new Scene.LineItem();
        it.wordCount = 1;
        it.words[0] = "Hi";
        it.wordX[0] = -20;
        it.wordColor[0] = 0xFFFFE066;
        it.wordScale[0] = 1f;
        final java.util.List<float[]> fill = new java.util.ArrayList<float[]>();
        final java.util.List<float[]> outline = new java.util.ArrayList<float[]>();
        final java.util.List<Integer> colors = new java.util.ArrayList<Integer>();
        glyphs.emitLine(it, false, 0, new GlyphAtlas.QuadSink() {
            @Override public void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                                       float u0, float v0, float u1, float v1, int argb) {
                fill.add(new float[]{x0, y0, x2, y2, u0, v0});
                colors.add(argb);
            }
        });
        glyphs.emitLine(it, true, 0x101010, new GlyphAtlas.QuadSink() {
            @Override public void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                                       float u0, float v0, float u1, float v1, int argb) {
                outline.add(new float[]{x0, y0, x2, y2, u0, v0});
                colors.add(argb);
            }
        });
        assertEquals(2, fill.size());
        assertEquals(2, outline.size());
        assertNotEquals("outline samples another part of the atlas", fill.get(0)[5], outline.get(0)[5], 0f);
        assertEquals(0xFFFFE066, (int) colors.get(0));
        assertEquals(0xFF101010, (int) colors.get(2));
        assertTrue(fill.get(0)[2] > fill.get(0)[0] && fill.get(0)[3] > fill.get(0)[1]);

        // roll rotates the quads
        it.rollDeg = 20;
        final float[] rolled = new float[1];
        glyphs.emitLine(it, false, 0, new GlyphAtlas.QuadSink() {
            @Override public void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                                       float u0, float v0, float u1, float v1, int argb) {
                if (rolled[0] == 0) rolled[0] = y0;
            }
        });
        assertNotEquals(fill.get(0)[1], rolled[0], 0.01f);
    }

    @Test
    public void rockModeKeepsLinesInsideTheScreenWidth() {
        viewMode();
        s.rows = 1;
        StringBuilder sb = new StringBuilder("[00:01.00]");
        for (int i = 0; i < 40; i++) sb.append("wonderful ");
        Lyrics l = LrcParser.parse(sb + "\n[00:20.00]x", "t", true);
        snap = new LyricsSnapshot(l, new EmojiEvent[0]);
        Scene.LineItem it = frame(2_000).lines[0];
        assertTrue(it.scale * it.widthPx <= s.distance * 1.31f);
    }
}
