package dev.lyricsworld.scene;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lyricsworld.util.Json;
import dev.lyricsworld.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Index of the lyrics font atlas ({@code textures/font_atlas.png}, Rock Salt, Apache-2.0): a white fill
 * glyph per character plus a second block of outline masks. Also lays out and "emits" text as textured
 * quads, so every Minecraft version only has to draw quads (see {@link QuadSink}).
 */
public final class GlyphAtlas {
    public static final String TEXTURE_PATH = "assets/lyricsworld/textures/font_atlas.png";
    private static final String INDEX_PATH = "/assets/lyricsworld/font/glyphs.json";
    private static final int MAX_CHAR = 0x2700;

    /** Receives one quad with 4 corners (top-left, bottom-left, bottom-right, top-right) in line-local pixels, y down. */
    public interface QuadSink {
        void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                  float u0, float v0, float u1, float v1, int argb);
    }

    private static final class Glyph {
        int cell = -1;
        float advance;
        float ox, oy, q = 1f;
    }

    private final Glyph[] glyphs = new Glyph[MAX_CHAR];
    private float size = 42f, cell = 104f, ascent = 42f, descent = 10f;
    private int cols = 19, count;
    private float texW = 1976f, texH = 2184f;
    private boolean loaded;

    public static GlyphAtlas loadDefault() {
        GlyphAtlas a = new GlyphAtlas();
        try (InputStream in = GlyphAtlas.class.getResourceAsStream(INDEX_PATH)) {
            if (in == null) throw new IOException("glyphs.json missing from the mod jar");
            JsonObject o = Json.obj(Json.readAll(in));
            a.size = Json.num(o, "size", 42);
            a.cell = Json.num(o, "cell", 104);
            a.cols = (int) Json.num(o, "cols", 19);
            a.texW = Json.num(o, "width", 1976);
            a.texH = Json.num(o, "height", 2184);
            a.count = (int) Json.num(o, "count", 0);
            a.ascent = Json.num(o, "ascent", 42);
            a.descent = Json.num(o, "descent", 10);
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("glyphs").entrySet()) {
                if (e.getKey().isEmpty()) continue;
                char ch = e.getKey().charAt(0);
                if (ch >= MAX_CHAR) continue;
                JsonObject g = e.getValue().getAsJsonObject();
                Glyph gl = new Glyph();
                gl.cell = (int) Json.num(g, "c", -1);
                gl.advance = g.get("a").getAsFloat();
                if (g.has("ox")) gl.ox = g.get("ox").getAsFloat();
                if (g.has("oy")) gl.oy = g.get("oy").getAsFloat();
                if (g.has("q")) gl.q = g.get("q").getAsFloat();
                a.glyphs[ch] = gl;
            }
            a.loaded = a.count > 0;
        } catch (IOException | RuntimeException e) {
            Log.warn("Lyrics font atlas unavailable - falling back to the game font", e);
        }
        return a;
    }

    public boolean isLoaded() { return loaded; }

    /** Height of one text line in atlas pixels. */
    public float lineHeight() { return ascent + descent; }

    private Glyph glyph(char c) {
        Glyph g = c < MAX_CHAR ? glyphs[c] : null;
        return g != null ? g : glyphs['?'];
    }

    public float advance(char c) {
        Glyph g = glyph(c);
        return g == null ? size * 0.4f : g.advance;
    }

    public float width(String s) {
        float w = 0;
        for (int i = 0; i < s.length(); i++) w += advance(s.charAt(i));
        return w;
    }

    /**
     * Emits every glyph of the line (all visible words) as quads. Run it twice: first with
     * {@code outlinePass = true} (dark halo), then with false (coloured fill).
     * Word pop-scaling and the line's roll are applied here, so renderers need nothing extra.
     */
    public void emitLine(Scene.LineItem line, boolean outlinePass, int outlineRgb, QuadSink sink) {
        float top = -lineHeight() / 2f;
        float baseline = top + ascent;
        double rad = Math.toRadians(line.rollDeg);
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        for (int w = 0; w < line.wordCount; w++) {
            String text = line.words[w];
            int argb = line.wordColor[w];
            if (outlinePass) argb = (argb & 0xFF000000) | (outlineRgb & 0xFFFFFF);
            float k = line.wordScale[w];
            float cx = line.wordX[w] + width(text) / 2f;
            float cy = baseline - ascent / 2f;
            float pen = line.wordX[w];
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                Glyph g = glyph(ch);
                if (g != null && g.cell >= 0) {
                    int cellNo = outlinePass ? count + g.cell : g.cell;
                    float u0 = (cellNo % cols) * cell / texW, v0 = (cellNo / cols) * cell / texH;
                    float u1 = u0 + cell / texW, v1 = v0 + cell / texH;
                    float side = cell * g.q;
                    float x0 = pen + g.ox, y0 = baseline + g.oy, x1 = x0 + side, y1 = y0 + side;
                    // pop: scale about the word's centre
                    x0 = cx + (x0 - cx) * k;
                    x1 = cx + (x1 - cx) * k;
                    y0 = cy + (y0 - cy) * k;
                    y1 = cy + (y1 - cy) * k;
                    sink.quad(rx(x0, y0, cos, sin), ry(x0, y0, cos, sin),
                            rx(x0, y1, cos, sin), ry(x0, y1, cos, sin),
                            rx(x1, y1, cos, sin), ry(x1, y1, cos, sin),
                            rx(x1, y0, cos, sin), ry(x1, y0, cos, sin),
                            u0, v0, u1, v1, argb);
                }
                pen += g == null ? size * 0.4f : g.advance;
            }
        }
    }

    private static float rx(float x, float y, float c, float s) { return x * c - y * s; }

    private static float ry(float x, float y, float c, float s) { return x * s + y * c; }
}
