package dev.lyricsworld.util;

public final class Version {
    /** Overwritten at runtime from the jar manifest when available. */
    public static final String VERSION = resolve();

    private Version() {}

    private static String resolve() {
        String v = Version.class.getPackage() == null ? null : Version.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }
}
