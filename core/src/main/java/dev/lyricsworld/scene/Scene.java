package dev.lyricsworld.scene;

/**
 * Everything to draw this frame, in world coordinates. All arrays are pre-allocated and reused:
 * the renderer must treat the content as read-only and must not keep references after {@code draw}.
 */
public final class Scene {
    public static final int MAX_LINES = 9;
    public static final int MAX_WORDS = 48;
    public static final int MAX_EMOJI = 32;

    /** One billboard text line. The line is centred on (x,y,z); word offsets are in font pixels. */
    public static final class LineItem {
        public double x, y, z;
        /** World units per font pixel. */
        public float scale;
        public int wordCount;
        public final String[] words = new String[MAX_WORDS];
        /** Pixel offset of each word's left edge from the line centre. */
        public final float[] wordX = new float[MAX_WORDS];
        /** ARGB, alpha already includes every fade. */
        public final int[] wordColor = new int[MAX_WORDS];
        /** 1 = normal; the word being sung pops slightly larger. */
        public final float[] wordScale = new float[MAX_WORDS];
        public float widthPx;
    }

    /** One emoji quad, centred on (x,y,z), always facing the camera. */
    public static final class EmojiItem {
        public double x, y, z;
        /** Edge length of the quad in blocks. */
        public float size;
        public float alpha;
        public int cell;
    }

    public final LineItem[] lines = new LineItem[MAX_LINES];
    public final EmojiItem[] emoji = new EmojiItem[MAX_EMOJI];
    public int lineCount;
    public int emojiCount;
    public boolean shadow = true;
    public boolean outline = true;
    public boolean seeThrough = true;
    /** Outline colour as ARGB with full alpha; the renderer scales alpha with the text alpha. */
    public int outlineRgb = 0x000000;

    public Scene() {
        for (int i = 0; i < lines.length; i++) lines[i] = new LineItem();
        for (int i = 0; i < emoji.length; i++) emoji[i] = new EmojiItem();
    }

    public void clear() {
        lineCount = 0;
        emojiCount = 0;
    }
}
