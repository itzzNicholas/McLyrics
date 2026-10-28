package dev.lyricsworld.config;

import dev.lyricsworld.util.Json;
import dev.lyricsworld.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Loads/saves {@link Settings}. The live instance is replaced atomically, readers just read the field. */
public final class SettingsStore {
    private final Path file;
    private volatile Settings current;

    public SettingsStore(Path file) {
        this.file = file;
        this.current = load();
    }

    public Settings get() { return current; }

    public synchronized void update(Settings next) {
        current = next.sanitize();
        save();
    }

    /** Replace by a JSON document (used by the external window). Unknown/invalid -> keeps old settings. */
    public synchronized boolean updateFromJson(String json) {
        try {
            Settings s = Json.COMPACT.fromJson(json, Settings.class);
            if (s == null) return false;
            update(s);
            return true;
        } catch (RuntimeException e) {
            Log.warn("Rejected invalid settings payload", e);
            return false;
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, Json.GSON.toJson(current).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Log.warn("Cannot save settings", e);
        }
    }

    private Settings load() {
        try {
            if (Files.exists(file)) {
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                Settings s = Json.COMPACT.fromJson(text, Settings.class);
                if (s != null) {
                    // files written before the layout redesign have no layoutVersion: give them the new look once
                    if (!Json.obj(text).has("layoutVersion")) s.migrateLayoutToDefaults();
                    return s.sanitize();
                }
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Settings unreadable, using defaults", e);
        }
        return new Settings().sanitize();
    }
}
