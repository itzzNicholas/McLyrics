package dev.lyricsworld.scene;

import java.util.Random;

/** Fixed-size pool of short-lived floating emoji. All state is relative to the player so they travel with them. */
final class EmojiParticles {
    static final int CAPACITY = Scene.MAX_EMOJI;
    static final float POP_SECONDS = 0.25f;
    static final float FADE_SECONDS = 0.7f;

    final boolean[] active = new boolean[CAPACITY];
    final long[] bornNanos = new long[CAPACITY];
    final float[] lifeSec = new float[CAPACITY];
    /** Degrees relative to the base direction (behind/front of the player). */
    final float[] relAngle = new float[CAPACITY];
    final float[] dist = new float[CAPACITY];
    final float[] height = new float[CAPACITY];
    /** Offset along the camera's right vector, in blocks. */
    final float[] lateral = new float[CAPACITY];
    final float[] size = new float[CAPACITY];
    final float[] phase = new float[CAPACITY];
    final int[] cell = new int[CAPACITY];

    int activeCount() {
        int n = 0;
        for (boolean a : active) if (a) n++;
        return n;
    }

    /** @return false if the pool is full or {@code limit} particles are already alive. */
    boolean spawn(long nowNanos, float lifeSeconds, float relAngleDeg, float distance, float h, float lat,
                  float sizeBlocks, int cellNo, Random rnd, int limit) {
        if (activeCount() >= limit) return false;
        for (int i = 0; i < CAPACITY; i++) {
            if (active[i]) continue;
            active[i] = true;
            bornNanos[i] = nowNanos;
            lifeSec[i] = lifeSeconds;
            relAngle[i] = relAngleDeg;
            dist[i] = distance;
            height[i] = h;
            lateral[i] = lat;
            size[i] = sizeBlocks;
            phase[i] = rnd.nextFloat() * 6.2831f;
            cell[i] = cellNo;
            return true;
        }
        return false;
    }

    void clear() {
        for (int i = 0; i < CAPACITY; i++) active[i] = false;
    }

    /** Pop-in with a little overshoot. */
    static float easeOutBack(float t) {
        if (t >= 1f) return 1f;
        float c1 = 1.70158f, c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
}
