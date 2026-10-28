package dev.lyricsworld.config;

import java.util.ArrayList;
import java.util.List;

/**
 * All user-tunable options. Plain public fields so Gson can (de)serialise it and the
 * external window can edit it through the local control API. Always call {@link #sanitize()}
 * after loading or mutating from untrusted input.
 */
public final class Settings {
    // ---- general ----
    public boolean enabled = true;
    public boolean showExternalWindow = true;
    /** AUTO | SPOTIFY | APPLE_MUSIC | SYSTEM */
    public String source = "AUTO";
    public String spotifyClientId = "";
    public int spotifyRedirectPort = 8898;
    /** Manual latency compensation, positive = lyrics appear earlier. */
    public int syncOffsetMs = 0;
    /** When only line timestamps exist, interpolate word timings. */
    public boolean allowEstimatedSync = true;
    public boolean fetchLyricsOnline = true;

    // ---- world rendering ----
    /** BEHIND | ABOVE | FRONT */
    public String position = "BEHIND";
    public float distance = 3.0f;
    public float height = 2.7f;
    public float scale = 1.0f;
    public float opacity = 0.95f;
    public int rows = 3;
    /** Degrees of arc step between consecutive rows (0 = straight stack). */
    public float arcSpread = 12f;
    /** NORMAL | BOLD | ITALIC | BOLD_ITALIC */
    public String fontStyle = "NORMAL";
    public int colorCurrent = 0xFFFFE066;
    public int colorSung = 0xFFB0B7C3;
    public int colorUpcoming = 0xFFFFFFFF;
    public boolean shadow = true;
    public boolean outline = true;
    public boolean seeThroughBlocks = true;
    public boolean fadeWhenMoving = true;
    public float movingOpacity = 0.30f;

    // ---- emoji ----
    public boolean emojiEnabled = true;
    /** 0..100 */
    public int emojiIntensity = 60;
    public boolean emojiMainOnly = false;
    /** 0 (always the first emoji) .. 100 (fully random rotation). */
    public int emojiVariety = 70;
    public int emojiCooldownMs = 700;
    public float emojiSize = 1.0f;
    public List<String> disabledEmojiCategories = new ArrayList<String>();

    public Settings sanitize() {
        source = oneOf(source, "AUTO", "AUTO", "SPOTIFY", "APPLE_MUSIC", "SYSTEM");
        position = oneOf(position, "BEHIND", "BEHIND", "ABOVE", "FRONT");
        fontStyle = oneOf(fontStyle, "NORMAL", "NORMAL", "BOLD", "ITALIC", "BOLD_ITALIC");
        if (spotifyClientId == null) spotifyClientId = "";
        spotifyClientId = spotifyClientId.trim();
        spotifyRedirectPort = clamp(spotifyRedirectPort, 1024, 65535);
        syncOffsetMs = clamp(syncOffsetMs, -5000, 5000);
        distance = clamp(distance, 1f, 12f);
        height = clamp(height, -1f, 8f);
        scale = clamp(scale, 0.25f, 4f);
        opacity = clamp(opacity, 0.05f, 1f);
        rows = clamp(rows, 1, 7);
        arcSpread = clamp(arcSpread, 0f, 45f);
        movingOpacity = clamp(movingOpacity, 0.05f, 1f);
        emojiIntensity = clamp(emojiIntensity, 0, 100);
        emojiVariety = clamp(emojiVariety, 0, 100);
        emojiCooldownMs = clamp(emojiCooldownMs, 0, 10000);
        emojiSize = clamp(emojiSize, 0.25f, 3f);
        if (disabledEmojiCategories == null) disabledEmojiCategories = new ArrayList<String>();
        return this;
    }

    public boolean isCategoryEnabled(String category) {
        return !disabledEmojiCategories.contains(category);
    }

    private static String oneOf(String v, String def, String... allowed) {
        if (v != null) {
            for (String a : allowed) if (a.equalsIgnoreCase(v)) return a;
        }
        return def;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static float clamp(float v, float lo, float hi) {
        if (Float.isNaN(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }
}
