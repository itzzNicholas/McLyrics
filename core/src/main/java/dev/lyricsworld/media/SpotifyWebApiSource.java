package dev.lyricsworld.media;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lyricsworld.util.Http;
import dev.lyricsworld.util.Json;

import java.io.IOException;
import java.util.Collections;

/** Reads the playback state from the Spotify Web API (works with any Spotify client, also on phones). */
public final class SpotifyWebApiSource implements MediaSource {
    private static final String URL = "https://api.spotify.com/v1/me/player/currently-playing";
    private final SpotifyAuth auth;
    private volatile String error;
    private volatile long retryAfterNanos;

    public SpotifyWebApiSource(SpotifyAuth auth) { this.auth = auth; }

    @Override public String name() { return "Spotify Web API"; }

    @Override public boolean isAvailable() { return auth.isConnected(); }

    @Override public String lastError() { return error; }

    @Override public void close() { }

    @Override
    public PlaybackState poll() {
        if (System.nanoTime() < retryAfterNanos) return null;
        String token = auth.accessToken();
        if (token == null) {
            error = auth.status();
            return null;
        }
        try {
            Http.Result r = Http.get(URL, Collections.singletonMap("Authorization", "Bearer " + token));
            // The API reports progress as of the response; assume the server sampled it mid-flight.
            long sampled = System.nanoTime() - (r.roundTripMs * 1_000_000L) / 2;
            if (r.code == 204 || r.body.isEmpty()) {
                error = null;
                return null;
            }
            if (r.code == 429) {
                retryAfterNanos = System.nanoTime() + 10_000_000_000L;
                error = "Spotify rate limit hit, slowing down";
                return null;
            }
            if (!r.ok()) {
                error = "Spotify HTTP " + r.code;
                return null;
            }
            return parse(r.body, sampled);
        } catch (IOException e) {
            error = "Spotify unreachable: " + e.getMessage();
            return null;
        }
    }

    static PlaybackState parse(String body, long sampledAtNanos) {
        JsonObject o = Json.obj(body);
        JsonElement item = o.get("item");
        if (item == null || !item.isJsonObject()) return null; // ads / podcasts without item
        JsonObject t = item.getAsJsonObject();
        if (!"track".equals(Json.str(o, "currently_playing_type", "track"))) return null;
        String artist = "";
        JsonElement arts = t.get("artists");
        if (arts != null && arts.isJsonArray() && arts.getAsJsonArray().size() > 0) {
            artist = Json.str(arts.getAsJsonArray().get(0).getAsJsonObject(), "name", "");
        }
        JsonElement alb = t.get("album");
        String album = alb != null && alb.isJsonObject() ? Json.str(alb.getAsJsonObject(), "name", "") : "";
        return new PlaybackState(Json.str(t, "name", ""), artist, album,
                Json.num(t, "duration_ms", 0), Json.num(o, "progress_ms", 0),
                Json.bool(o, "is_playing", false), sampledAtNanos, "Spotify");
    }
}
