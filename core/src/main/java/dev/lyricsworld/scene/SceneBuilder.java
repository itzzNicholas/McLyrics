package dev.lyricsworld.scene;

import dev.lyricsworld.config.Settings;
import dev.lyricsworld.emoji.EmojiAtlas;
import dev.lyricsworld.emoji.EmojiEvent;
import dev.lyricsworld.lyrics.Lyrics;
import dev.lyricsworld.lyrics.LyricsSnapshot;

import java.util.Random;

/**
 * Turns (lyrics, playback position, player, settings) into a {@link Scene}. All the interesting
 * behaviour lives here, once, for every Minecraft version: row layout, word-by-word highlighting,
 * fades, emoji spawning and animation. After warm-up a frame allocates nothing.
 */
public final class SceneBuilder {
    private static final char SECTION = '§';
    private static final float WORLD_PER_PIXEL = 0.025f;
    /** The Rock Salt atlas is drawn at 42 px per em: this keeps its on-screen size comparable. */
    private static final float GLYPH_WORLD_PER_PIXEL = 0.0034f;
    /** Widest a line may get in blocks before it is shrunk to fit. */
    private static final float MAX_LINE_BLOCKS = 7f;
    private static final float EMOJI_LIFE_SECONDS = 2.6f;
    private static final int LAYOUT_CACHE = 16;

    private static final class Layout {
        Lyrics.Line line;
        String stylePrefix;
        final String[] words = new String[Scene.MAX_WORDS];
        final float[] x = new float[Scene.MAX_WORDS];
        final float[] w = new float[Scene.MAX_WORDS];
        int count;
        float width;
    }

    private final LyricsRenderer metrics;
    private final EmojiAtlas atlas;
    private final GlyphAtlas glyphAtlas;
    private final Scene scene = new Scene();
    private final Layout[] cache = new Layout[LAYOUT_CACHE];
    private final EmojiParticles particles = new EmojiParticles();
    private final Random rnd = new Random();

    private float moveFade = 1f;
    private long lastNanos;
    private LyricsSnapshot lastSnapshot;
    private int nextEvent;
    private long lastPosMs = -1;
    private long lastSpawnNanos;
    private int lastEmojiIdx = -1;
    private int cacheCursor;

    // per-frame derived state
    private boolean useGlyphs;
    private boolean viewMode;
    private float distScale = 1f;
    private double camX, camY, camZ;
    private double fwdX, fwdY, fwdZ, rightX, rightZ, upX, upY, upZ;

    public SceneBuilder(LyricsRenderer metrics, EmojiAtlas atlas, GlyphAtlas glyphAtlas) {
        this.metrics = metrics;
        this.atlas = atlas;
        this.glyphAtlas = glyphAtlas;
        for (int i = 0; i < cache.length; i++) cache[i] = new Layout();
    }

    /** Drop transient animation state (track change, world change). */
    public void reset() {
        particles.clear();
        lastSnapshot = null;
        lastPosMs = -1;
    }

    /**
     * @param snap   lyrics + emoji events of the playing track, or null
     * @param posMs  playback position, latency offset already applied
     * @return the (reused) scene, possibly empty
     */
    public Scene build(PlayerView v, Settings s, LyricsSnapshot snap, long posMs, boolean playing, long nowNanos) {
        scene.clear();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.25f, (nowNanos - lastNanos) / 1e9f);
        lastNanos = nowNanos;

        // smooth fade towards the "moving/fighting" opacity so the view is never blocked mid-combat
        float target = s.fadeWhenMoving && (v.moving || v.fighting) ? s.movingOpacity / s.opacity : 1f;
        moveFade += (Math.min(1f, target) - moveFade) * Math.min(1f, dt * 6f);

        scene.shadow = s.shadow;
        scene.outline = s.outline;
        scene.seeThrough = s.seeThroughBlocks;
        useGlyphs = "ROCK".equals(s.fontMode) && glyphAtlas != null && glyphAtlas.isLoaded();
        scene.customFont = useGlyphs;
        scene.glyphs = useGlyphs ? glyphAtlas : null;
        viewMode = "VIEW".equals(s.position);
        distScale = s.distance / 3f;
        if (viewMode) computeViewBasis(v);

        if (snap != lastSnapshot) particles.clear();
        float baseYaw = baseYaw(s, v);
        float ws = (useGlyphs ? GLYPH_WORLD_PER_PIXEL * distScale : WORLD_PER_PIXEL) * s.scale;
        float rowSpacing = lineHeight() * ws * 1.3f;

        int current = -1;
        if (snap != null && snap.lyrics.hasText()) {
            current = snap.lyrics.lineIndexAt(posMs);
            buildLines(v, s, snap, current, posMs, baseYaw, ws, rowSpacing);
        }
        handleEmoji(v, s, snap, current, posMs, playing, nowNanos, baseYaw, ws, rowSpacing);
        lastSnapshot = snap;
        lastPosMs = posMs;
        return scene;
    }

    // ------------------------------------------------------------------ text lines

    private float maxLineBlocks() { return viewMode ? Math.max(1.5f, distScale * 3.9f) : MAX_LINE_BLOCKS; }

    private void computeViewBasis(PlayerView v) {
        double yaw = Math.toRadians(v.camYawDeg), pitch = Math.toRadians(v.camPitchDeg);
        double cp = Math.cos(pitch);
        fwdX = -Math.sin(yaw) * cp;
        fwdY = -Math.sin(pitch);
        fwdZ = Math.cos(yaw) * cp;
        rightX = -Math.cos(yaw);
        rightZ = -Math.sin(yaw);
        // up = right x forward
        upX = -rightZ * fwdY;
        upY = rightZ * fwdX - rightX * fwdZ;
        upZ = rightX * fwdY;
        camX = v.camX;
        camY = v.camY;
        camZ = v.camZ;
    }

    /** Which side of the screen a line sits on: alternates left/right, with a stable per-line jitter. */
    private static float sideOf(int lineIndex) {
        float mag = 0.55f + 0.45f * (((lineIndex * 2654435761L) >>> 8 & 255) / 255f);
        return ((lineIndex & 1) == 0 ? -1f : 1f) * mag;
    }

    private void buildLines(PlayerView v, Settings s, LyricsSnapshot snap, int current, long posMs,
                            float baseYaw, float ws, float rowSpacing) {
        Lyrics lyrics = snap.lyrics;
        int n = lyrics.lines.size();
        int rows = s.rows;
        int past = rows <= 3 ? (rows - 1) / 2 : (rows - 1) / 3;
        int future = rows - 1 - past;

        // Before the first line the "current" row is the upcoming first line.
        int anchor = current < 0 ? 0 : current;
        float idle = idleFade(lyrics, current, posMs);
        float common = s.opacity * moveFade * idle;
        if (common <= 0.01f) return;

        for (int o = -past; o <= future && scene.lineCount < Scene.MAX_LINES; o++) {
            int li = anchor + o;
            if (current < 0) li = o + 0; // intro: show upcoming lines only
            if (li < 0 || li >= n) continue;
            Lyrics.Line line = lyrics.lines.get(li);
            if (line.isBlank()) continue;
            Layout lay = layoutFor(line, s.fontStyle);
            if (lay.count == 0) continue;

            float rowFade = (float) Math.pow(0.62, Math.abs(o));
            if (current < 0 && o < 0) continue;
            Scene.LineItem it = scene.lines[scene.lineCount];
            float shrink = Math.min(1f, maxLineBlocks() / Math.max(1f, lay.width * ws));
            it.scale = ws * shrink;
            it.widthPx = lay.width;
            placeRow(it, v, s, baseYaw, o, rowSpacing, li);

            boolean isCurrent = li == current;
            boolean isPast = current >= 0 && li < current;
            int visible = 0;
            for (int w = 0; w < lay.count; w++) {
                float alpha = common * rowFade;
                int rgb;
                float wscale = 1f;
                if (isCurrent) {
                    Lyrics.Word word = line.words[Math.min(w, line.words.length - 1)];
                    if (posMs >= word.endMs) {
                        rgb = s.colorSung;
                        alpha *= 0.85f;
                    } else if (posMs >= word.startMs) {
                        float t = (posMs - word.startMs) / (float) Math.max(1, word.endMs - word.startMs);
                        rgb = mix(s.colorUpcoming, s.colorCurrent, Math.min(1f, t * 3f));
                        wscale = 1f + 0.18f * (1f - t);
                    } else {
                        rgb = s.colorUpcoming;
                        alpha *= 0.7f;
                    }
                } else if (isPast) {
                    rgb = s.colorSung;
                    alpha *= 0.7f;
                } else {
                    rgb = s.colorUpcoming;
                    alpha *= 0.6f;
                }
                int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
                if (a < 6) continue; // Minecraft's font treats very low alpha as opaque - skip instead
                it.words[visible] = lay.words[w];
                it.wordX[visible] = lay.x[w] - lay.width / 2f;
                it.wordColor[visible] = (a << 24) | (rgb & 0xFFFFFF);
                it.wordScale[visible] = wscale;
                visible++;
            }
            it.wordCount = visible;
            if (visible > 0) scene.lineCount++;
        }
    }

    /** Fade the whole display out during long instrumental gaps, back in just before the next line. */
    private static float idleFade(Lyrics lyrics, int current, long posMs) {
        if (current < 0) {
            long first = lyrics.lines.get(0).startMs;
            return first - posMs > 4000 ? 0f : 1f;
        }
        Lyrics.Line line = lyrics.lines.get(current);
        long since = posMs - line.endMs;
        if (since < 2500) return 1f;
        long untilNext = current + 1 < lyrics.lines.size() ? lyrics.lines.get(current + 1).startMs - posMs : Long.MAX_VALUE;
        if (untilNext < 1500) return 1f;
        float out = 1f - (since - 2500) / 1000f;
        return Math.max(0f, out);
    }

    private void placeRow(Scene.LineItem it, PlayerView v, Settings s, float baseYaw, int rowOffset, float rowSpacing,
                          int lineIndex) {
        if (viewMode) {
            float side = sideOf(lineIndex);
            double lateral = side * s.spread * distScale;
            double vert = s.height * distScale - rowOffset * rowSpacing;
            it.x = camX + fwdX * s.distance + rightX * lateral + upX * vert;
            it.y = camY + fwdY * s.distance + upY * vert;
            it.z = camZ + fwdZ * s.distance + rightZ * lateral + upZ * vert;
            it.rollDeg = -Math.signum(side) * s.tilt;
            return;
        }
        it.rollDeg = 0f;
        double y = v.y + s.height - rowOffset * rowSpacing;
        if ("ABOVE".equals(s.position)) {
            it.x = v.x;
            it.z = v.z;
        } else {
            float ang = baseYaw + rowOffset * s.arcSpread;
            double rad = Math.toRadians(ang);
            it.x = v.x - Math.sin(rad) * s.distance;
            it.z = v.z + Math.cos(rad) * s.distance;
        }
        it.y = y;
    }

    private static float baseYaw(Settings s, PlayerView v) {
        return "FRONT".equals(s.position) ? v.yawDeg : v.yawDeg + 180f;
    }

    private float textWidth(String text) { return useGlyphs ? glyphAtlas.width(text) : metrics.textWidth(text); }

    private float lineHeight() { return useGlyphs ? glyphAtlas.lineHeight() : metrics.lineHeight(); }

    private Layout layoutFor(Lyrics.Line line, String fontStyle) {
        String prefix = useGlyphs ? "\u0000" : stylePrefix(fontStyle); // glyph layouts carry no style codes
        for (Layout l : cache) {
            if (l.line == line && l.stylePrefix.equals(prefix)) return l;
        }
        Layout l = cache[cacheCursor];
        cacheCursor = (cacheCursor + 1) % cache.length;
        l.line = line;
        l.stylePrefix = prefix;
        float spacing = textWidth(useGlyphs ? " " : prefix + " ");
        float x = 0;
        int count = 0;
        for (Lyrics.Word w : line.words) {
            if (count >= Scene.MAX_WORDS) break;
            String clean = w.text.replace(SECTION, ' ');
            String styled = useGlyphs ? clean : prefix + clean;
            l.words[count] = styled;
            l.w[count] = textWidth(styled);
            l.x[count] = x;
            x += l.w[count] + spacing;
            count++;
        }
        l.count = count;
        l.width = Math.max(0f, x - spacing);
        return l;
    }

    private static String stylePrefix(String fontStyle) {
        if ("BOLD".equals(fontStyle)) return SECTION + "l";
        if ("ITALIC".equals(fontStyle)) return SECTION + "o";
        if ("BOLD_ITALIC".equals(fontStyle)) return SECTION + "l" + SECTION + "o";
        return "";
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    // ------------------------------------------------------------------ emoji

    private void handleEmoji(PlayerView v, Settings s, LyricsSnapshot snap, int current, long posMs,
                             boolean playing, long nowNanos, float baseYaw, float ws, float rowSpacing) {
        if (!s.emojiEnabled || s.emojiIntensity <= 0) {
            particles.clear();
            return;
        }
        if (snap != lastSnapshot || lastPosMs < 0 || Math.abs(posMs - lastPosMs) > 1500) {
            // new track or a seek: continue from the new position without firing everything we skipped
            nextEvent = snap == null ? 0 : snap.firstEventAtOrAfter(posMs);
        }
        if (snap != null && playing) {
            EmojiEvent[] ev = snap.events;
            while (nextEvent < ev.length && ev[nextEvent].timeMs <= posMs) {
                EmojiEvent e = ev[nextEvent++];
                if (posMs - e.timeMs > 900) continue; // too late, the moment has passed
                trySpawn(e, v, s, snap, current, nowNanos, baseYaw, ws, rowSpacing);
            }
        }
        writeParticles(v, s, nowNanos, baseYaw);
    }

    private void trySpawn(EmojiEvent e, PlayerView v, Settings s, LyricsSnapshot snap, int current, long nowNanos,
                          float baseYaw, float ws, float rowSpacing) {
        if (!s.isCategoryEnabled(e.category)) return;
        if (s.emojiMainOnly && !e.main) return;
        long cooldownNanos = s.emojiCooldownMs * 1_000_000L;
        if (nowNanos - lastSpawnNanos < cooldownNanos) return;
        float p = s.emojiIntensity / 100f;
        if (e.main) p = Math.min(1f, p * 1.4f);
        if (rnd.nextFloat() > p) return;

        int count = 1 + (s.emojiIntensity >= 75 ? 1 : 0) + (s.emojiIntensity >= 95 ? 1 : 0);
        count = Math.min(count, e.emoji.length);
        int limit = 6 + s.emojiIntensity / 8;

        int rowOffset = current < 0 ? 0 : Math.max(-3, Math.min(3, e.lineIndex - current));
        Lyrics.Line line = snap.lyrics.lines.get(e.lineIndex);
        Layout lay = layoutFor(line, s.fontStyle);
        float shrink = Math.min(1f, maxLineBlocks() / Math.max(1f, lay.width * ws));
        int wi = Math.min(e.wordIndex, Math.max(0, lay.count - 1));
        float centreX = lay.count == 0 ? 0 : lay.x[wi] + lay.w[wi] / 2f - lay.width / 2f;
        float size = 0.55f * s.emojiSize * (0.7f + 0.6f * s.emojiIntensity / 100f) * (float) Math.sqrt(s.scale) * distScale;
        float relAngle = "ABOVE".equals(s.position) ? 0f : rowOffset * s.arcSpread;
        float dist = "ABOVE".equals(s.position) ? 0f : s.distance;
        float lineBaseHeight = viewMode ? s.height * distScale - rowOffset * rowSpacing : s.height - rowOffset * rowSpacing;

        int first = pickIndex(e.emoji.length, s.emojiVariety);
        boolean any = false;
        for (int k = 0; k < count; k++) {
            int idx = (first + k) % e.emoji.length;
            int cell = atlas.cellOf(e.emoji[idx]);
            if (cell < 0) continue;
            float lateral;
            if (viewMode) {
                // scatter to the left and right of the line, mostly on the opposite side of the screen
                float lineSide = Math.signum(sideOf(e.lineIndex));
                float emojiSide = rnd.nextFloat() < 0.7f ? -lineSide : lineSide;
                lateral = emojiSide * (0.35f + rnd.nextFloat() * 1.1f) * s.spread * distScale
                        + (k - (count - 1) / 2f) * size * 1.05f;
            } else {
                lateral = centreX * ws * shrink + (k - (count - 1) / 2f) * size * 1.05f + (rnd.nextFloat() - 0.5f) * 0.2f;
            }
            float height = lineBaseHeight + lineHeight() * ws * 1.1f + size * 0.6f + rnd.nextFloat() * 0.35f;
            any |= particles.spawn(nowNanos, EMOJI_LIFE_SECONDS, relAngle, dist, height, lateral,
                    size * (0.85f + rnd.nextFloat() * 0.3f), cell, rnd, limit);
            lastEmojiIdx = idx;
        }
        if (any) lastSpawnNanos = nowNanos;
    }

    /** variety 0 = always the first (canonical) emoji of the rule, 100 = uniformly random, never the last one twice. */
    private int pickIndex(int n, int variety) {
        if (n <= 1) return 0;
        if (rnd.nextInt(100) >= variety) return 0;
        int idx = rnd.nextInt(n);
        if (idx == lastEmojiIdx) idx = (idx + 1) % n;
        return idx;
    }

    private void writeParticles(PlayerView v, Settings s, long nowNanos, float baseYaw) {
        // camera right vector in world space (horizontal)
        double camRad = Math.toRadians(v.camYawDeg);
        double rx = -Math.cos(camRad), rz = -Math.sin(camRad);
        EmojiParticles p = particles;
        for (int i = 0; i < EmojiParticles.CAPACITY && scene.emojiCount < Scene.MAX_EMOJI; i++) {
            if (!p.active[i]) continue;
            float age = (nowNanos - p.bornNanos[i]) / 1e9f;
            if (age >= p.lifeSec[i]) {
                p.active[i] = false;
                continue;
            }
            float pop = EmojiParticles.easeOutBack(Math.min(1f, age / EmojiParticles.POP_SECONDS));
            float remaining = p.lifeSec[i] - age;
            float fade = remaining < EmojiParticles.FADE_SECONDS ? remaining / EmojiParticles.FADE_SECONDS : 1f;
            float alpha = fade * s.opacity * Math.max(moveFade, 0.5f);
            if (alpha <= 0.02f) continue;
            float sway = (float) Math.sin(age * 3.0 + p.phase[i]) * 0.05f;
            Scene.EmojiItem it = scene.emoji[scene.emojiCount++];
            if (viewMode) {
                double lat = p.lateral[i] + sway;
                double vert = p.height[i] + 0.3f * age;
                it.x = camX + fwdX * p.dist[i] + rightX * lat + upX * vert;
                it.y = camY + fwdY * p.dist[i] + upY * vert;
                it.z = camZ + fwdZ * p.dist[i] + rightZ * lat + upZ * vert;
            } else {
                double rad = Math.toRadians(baseYaw + p.relAngle[i]);
                double bx = v.x - Math.sin(rad) * p.dist[i];
                double bz = v.z + Math.cos(rad) * p.dist[i];
                it.x = bx + rx * (p.lateral[i] + sway);
                it.z = bz + rz * (p.lateral[i] + sway);
                it.y = v.y + p.height[i] + 0.3f * age;
            }
            it.size = p.size[i] * pop;
            it.alpha = alpha;
            it.cell = p.cell[i];
        }
    }
}
