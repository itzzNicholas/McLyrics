package dev.lyricsworld.lyrics;

import com.google.gson.JsonObject;
import dev.lyricsworld.util.Json;
import dev.lyricsworld.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** On-disk cache of downloaded LRC text, including negative results ("not found") with a TTL. */
public final class LyricsCache {
    static final long MISS_TTL_MS = 24L * 3600 * 1000;
    private final Path dir;

    public LyricsCache(Path dir) { this.dir = dir; }

    public static final class Entry {
        public final boolean found;
        public final String lrc;
        public final String source;

        Entry(boolean found, String lrc, String source) {
            this.found = found;
            this.lrc = lrc;
            this.source = source;
        }
    }

    /** @return the cached entry or null when unknown / expired. */
    public Entry get(String key) {
        Path f = file(key);
        try {
            if (!Files.exists(f)) return null;
            JsonObject o = Json.obj(new String(Files.readAllBytes(f), StandardCharsets.UTF_8));
            long at = Json.num(o, "fetchedAt", 0);
            if ("found".equals(Json.str(o, "status", ""))) {
                return new Entry(true, Json.str(o, "lrc", ""), Json.str(o, "source", "cache"));
            }
            if (System.currentTimeMillis() - at < MISS_TTL_MS) return new Entry(false, null, null);
        } catch (IOException | RuntimeException e) {
            Log.warn("Lyrics cache entry unreadable: " + f.getFileName());
        }
        return null;
    }

    public void putFound(String key, String lrc, String source) {
        JsonObject o = new JsonObject();
        o.addProperty("status", "found");
        o.addProperty("source", source);
        o.addProperty("lrc", lrc);
        o.addProperty("fetchedAt", System.currentTimeMillis());
        write(key, o);
    }

    public void putMiss(String key) {
        JsonObject o = new JsonObject();
        o.addProperty("status", "missing");
        o.addProperty("fetchedAt", System.currentTimeMillis());
        write(key, o);
    }

    private void write(String key, JsonObject o) {
        try {
            Files.createDirectories(dir);
            Files.write(file(key), Json.COMPACT.toJson(o).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.warn("Cannot write lyrics cache", e);
        }
    }

    private Path file(String key) {
        String safe = key.length() > 120 ? key.substring(0, 100) + Integer.toHexString(key.hashCode()) : key;
        return dir.resolve(safe + ".json");
    }
}
