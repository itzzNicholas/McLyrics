package dev.lyricsworld.emoji;

import com.google.gson.JsonObject;
import dev.lyricsworld.util.Json;
import dev.lyricsworld.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Index of the emoji texture atlas ({@code textures/emoji_atlas.png}, Twemoji graphics, CC-BY 4.0).
 * Maps an emoji string to a cell and cell to UV rectangle. Pure data: loading the PNG into the GPU
 * is done by the version-specific renderer.
 */
public final class EmojiAtlas {
    public static final String TEXTURE_PATH = "assets/lyricsworld/textures/emoji_atlas.png";
    private static final String INDEX_PATH = "/assets/lyricsworld/emoji/atlas.json";

    private final int cell;
    private final int cols;
    private final int width;
    private final int height;
    private final Map<String, Integer> index;

    public EmojiAtlas(int cell, int cols, int width, int height, Map<String, Integer> index) {
        this.cell = cell;
        this.cols = cols;
        this.width = width;
        this.height = height;
        this.index = index;
    }

    public static EmojiAtlas loadDefault() {
        try (InputStream in = EmojiAtlas.class.getResourceAsStream(INDEX_PATH)) {
            if (in == null) throw new IOException("atlas.json missing from the mod jar");
            JsonObject o = Json.obj(Json.readAll(in));
            Map<String, Integer> idx = new HashMap<String, Integer>();
            for (Map.Entry<String, com.google.gson.JsonElement> e : o.getAsJsonObject("emoji").entrySet()) {
                idx.put(e.getKey(), e.getValue().getAsInt());
            }
            return new EmojiAtlas((int) Json.num(o, "cell", 64), (int) Json.num(o, "cols", 32),
                    (int) Json.num(o, "width", 2048), (int) Json.num(o, "height", 2048), idx);
        } catch (IOException | RuntimeException e) {
            Log.warn("Emoji atlas unavailable - emoji disabled", e);
            return new EmojiAtlas(64, 32, 2048, 2048, new HashMap<String, Integer>());
        }
    }

    /** Code points in hex joined by '-', with variation selectors (FE0F/FE0E) removed. */
    public static String key(String emoji) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < emoji.length(); ) {
            int cp = emoji.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 0xFE0F || cp == 0xFE0E) continue;
            if (sb.length() > 0) sb.append('-');
            sb.append(Integer.toHexString(cp));
        }
        return sb.toString();
    }

    /** Cell number for the emoji, or -1 if the atlas has no picture for it. */
    public int cellOf(String emoji) {
        Integer i = index.get(key(emoji));
        return i == null ? -1 : i;
    }

    public boolean has(String emoji) { return index.containsKey(key(emoji)); }

    public int size() { return index.size(); }

    // UV rectangle of a cell, inset by half a texel so linear filtering never bleeds neighbours in.
    public float u0(int cellNo) { return ((cellNo % cols) * cell + 0.5f) / width; }

    public float v0(int cellNo) { return ((cellNo / cols) * cell + 0.5f) / height; }

    public float u1(int cellNo) { return ((cellNo % cols) * cell + cell - 0.5f) / width; }

    public float v1(int cellNo) { return ((cellNo / cols) * cell + cell - 0.5f) / height; }
}
