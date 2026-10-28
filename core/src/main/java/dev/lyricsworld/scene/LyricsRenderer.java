package dev.lyricsworld.scene;

/**
 * The only thing that differs between Minecraft versions on the drawing side. Implementations draw a
 * {@link Scene} (billboard text lines + emoji quads, positions already in world space) and expose font
 * metrics for layout. Every method is called on the render thread.
 */
public interface LyricsRenderer {
    /** Width in font pixels of the (already style-prefixed) text, at scale 1. */
    int textWidth(String styledText);

    /** Height in font pixels of one text line (9 in vanilla). */
    int lineHeight();

    /**
     * Draw the scene. The caller has set up whatever per-frame context the implementation needs
     * (pose stack, camera...) through the implementation's own API. Must restore all GL state it changes.
     */
    void draw(Scene scene);
}
