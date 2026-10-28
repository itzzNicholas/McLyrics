package dev.lyricsworld.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lyricsworld.util.Http;
import dev.lyricsworld.util.Json;

import java.io.IOException;

/**
 * Client for LRCLIB (https://lrclib.net), a free, key-less, community-maintained database of
 * time-synced lyrics. Requests are made at runtime from the player's machine only; nothing is
 * bundled in the mod. See the README for the data-source notes.
 */
public final class LrcLibClient {
    private static final String BASE = "https://lrclib.net/api";

    /** @return synced LRC text, or null when nothing usable exists. @throws IOException on network trouble. */
    public String findSynced(String artist, String title, String album, long durationMs) throws IOException {
        String cleanTitle = TrackNames.cleanTitle(title);
        String cleanArtist = TrackNames.firstArtist(artist);

        String url = BASE + "/get?track_name=" + Http.enc(cleanTitle) + "&artist_name=" + Http.enc(cleanArtist)
                + (album == null || album.isEmpty() ? "" : "&album_name=" + Http.enc(album))
                + (durationMs > 0 ? "&duration=" + Math.round(durationMs / 1000.0) : "");
        Http.Result r = Http.get(url, null);
        if (r.ok()) {
            String synced = syncedOf(Json.obj(r.body));
            if (synced != null) return synced;
        } else if (r.code != 404) {
            throw new IOException("LRCLIB HTTP " + r.code);
        }

        // Fallback: fuzzy search, choose the synced result with the closest duration.
        Http.Result s = Http.get(BASE + "/search?track_name=" + Http.enc(cleanTitle)
                + "&artist_name=" + Http.enc(cleanArtist), null);
        if (!s.ok()) {
            if (s.code == 404) return null;
            throw new IOException("LRCLIB HTTP " + s.code);
        }
        return pickBest(s.body, durationMs);
    }

    static String pickBest(String searchBody, long durationMs) {
        JsonElement root = Json.parse(searchBody);
        if (!root.isJsonArray()) return null;
        JsonArray arr = root.getAsJsonArray();
        String best = null;
        double bestDiff = Double.MAX_VALUE;
        for (JsonElement e : arr) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String synced = syncedOf(o);
            if (synced == null) continue;
            double dur = o.has("duration") && !o.get("duration").isJsonNull() ? o.get("duration").getAsDouble() : 0;
            double diff = durationMs > 0 && dur > 0 ? Math.abs(dur * 1000 - durationMs) : 1000;
            if (durationMs > 0 && dur > 0 && diff > 4000) continue; // different recording
            if (diff < bestDiff) {
                bestDiff = diff;
                best = synced;
            }
        }
        return best;
    }

    private static String syncedOf(JsonObject o) {
        if (Json.bool(o, "instrumental", false)) return null;
        String s = Json.str(o, "syncedLyrics", null);
        return s == null || s.trim().isEmpty() ? null : s;
    }
}
