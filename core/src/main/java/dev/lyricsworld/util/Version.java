package dev.lyricsworld.util;

import java.io.IOException;
import java.io.InputStream;

public final class Version {
    /** From {@code assets/lyricsworld/version.txt} (written by the build), else the jar manifest, else "dev". */
    public static final String VERSION = resolve();

    private Version() {}

    private static String resolve() {
        try (InputStream in = Version.class.getResourceAsStream("/assets/lyricsworld/version.txt")) {
            if (in != null) {
                String v = Json.readAll(in).trim();
                if (!v.isEmpty()) return v;
            }
        } catch (IOException | RuntimeException ignored) {
            // fall through
        }
        Package p = Version.class.getPackage();
        String v = p == null ? null : p.getImplementationVersion();
        return v == null ? "dev" : v;
    }
}
