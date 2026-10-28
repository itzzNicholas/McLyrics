package dev.lyricsworld.scene;

/** Per-frame snapshot of the local player/camera, filled in by the version-specific code. */
public final class PlayerView {
    /** Player feet position, already interpolated with the partial tick. */
    public double x, y, z;
    /** Player facing in degrees, Minecraft convention (0 = +Z/south, 90 = -X/west). */
    public float yawDeg;
    /** Camera (eye) position and yaw in the same convention. */
    public double camX, camY, camZ;
    public float camYawDeg;
    /** Camera pitch in degrees, Minecraft convention (positive = looking down). */
    public float camPitchDeg;
    public boolean moving;
    public boolean fighting;
}
