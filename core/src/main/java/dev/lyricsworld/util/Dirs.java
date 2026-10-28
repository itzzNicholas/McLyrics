package dev.lyricsworld.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** All on-disk locations used by the mod, rooted at {@code <minecraft>/config/lyricsworld}. */
public final class Dirs {
    public final Path root;
    public final Path settingsFile;
    public final Path cacheDir;
    public final Path lyricsCacheDir;
    public final Path localLyricsDir;
    public final Path logsDir;
    public final Path tokenFile;
    public final Path logFile;

    public Dirs(Path gameDir) {
        this.root = gameDir.resolve("config").resolve("lyricsworld");
        this.settingsFile = root.resolve("settings.json");
        this.cacheDir = root.resolve("cache");
        this.lyricsCacheDir = cacheDir.resolve("lyrics");
        this.localLyricsDir = root.resolve("lyrics");
        this.logsDir = root.resolve("logs");
        this.tokenFile = root.resolve("spotify_token.bin");
        this.logFile = logsDir.resolve("lyricsworld.log");
    }

    public void create() {
        try {
            Files.createDirectories(lyricsCacheDir);
            Files.createDirectories(localLyricsDir);
            Files.createDirectories(logsDir);
        } catch (IOException e) {
            Log.warn("Cannot create mod directories under " + root, e);
        }
    }
}
