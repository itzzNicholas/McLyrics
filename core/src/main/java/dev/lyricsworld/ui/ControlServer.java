package dev.lyricsworld.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.lyricsworld.LyricsWorld;
import dev.lyricsworld.lyrics.Lyrics;
import dev.lyricsworld.lyrics.LyricsSnapshot;
import dev.lyricsworld.media.PlaybackState;
import dev.lyricsworld.util.Json;
import dev.lyricsworld.util.Log;
import dev.lyricsworld.util.MiniHttpServer;
import dev.lyricsworld.util.Version;

import java.io.IOException;
import java.security.SecureRandom;

/**
 * Loopback-only control API used by the external settings window (a separate process, so Swing never
 * shares a JVM with Minecraft's GLFW/LWJGL). Protected by a random per-launch token.
 *
 * <pre>
 * GET  /state            everything the window shows
 * POST /settings         full settings JSON
 * POST /spotify/login    -> {"url": "..."} to open in a browser
 * POST /spotify/logout
 * POST /toggle           enable/disable lyrics in-game
 * </pre>
 */
public final class ControlServer {
    private final LyricsWorld world;
    private final MiniHttpServer server;
    private final String token;

    public ControlServer(LyricsWorld world) throws IOException {
        this.world = world;
        byte[] b = new byte[18];
        new SecureRandom().nextBytes(b);
        this.token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        this.server = MiniHttpServer.start(0, new MiniHttpServer.Handler() {
            @Override public MiniHttpServer.Response handle(MiniHttpServer.Request r) { return route(r); }
        });
    }

    public int port() { return server.port(); }

    public String token() { return token; }

    public void close() { server.close(); }

    private MiniHttpServer.Response route(MiniHttpServer.Request r) {
        if (!token.equals(r.headers.get("x-lw-token"))) return MiniHttpServer.Response.text(401, "unauthorized");
        try {
            if ("GET".equals(r.method) && "/state".equals(r.path)) {
                return MiniHttpServer.Response.json(200, Json.COMPACT.toJson(state()));
            }
            if ("POST".equals(r.method)) {
                if ("/settings".equals(r.path)) {
                    return world.updateSettingsJson(r.body) ? MiniHttpServer.Response.json(200, "{}")
                            : MiniHttpServer.Response.text(400, "invalid settings");
                }
                if ("/toggle".equals(r.path)) {
                    world.toggle();
                    return MiniHttpServer.Response.json(200, "{}");
                }
                if ("/spotify/login".equals(r.path)) {
                    try {
                        world.media().spotifyAuth().setClientId(world.settings().get().spotifyClientId);
                        String url = world.media().spotifyAuth().beginLogin(world.settings().get().spotifyRedirectPort);
                        JsonObject o = new JsonObject();
                        o.addProperty("url", url);
                        return MiniHttpServer.Response.json(200, Json.COMPACT.toJson(o));
                    } catch (IOException e) {
                        JsonObject o = new JsonObject();
                        o.addProperty("error", String.valueOf(e.getMessage()));
                        return MiniHttpServer.Response.json(400, Json.COMPACT.toJson(o));
                    }
                }
                if ("/spotify/logout".equals(r.path)) {
                    world.media().spotifyAuth().disconnect();
                    return MiniHttpServer.Response.json(200, "{}");
                }
            }
            return MiniHttpServer.Response.text(404, "not found");
        } catch (RuntimeException e) {
            Log.warn("Control API error", e);
            return MiniHttpServer.Response.text(500, "error");
        }
    }

    JsonObject state() {
        JsonObject o = new JsonObject();
        o.addProperty("version", Version.VERSION);
        o.addProperty("minecraft", world.minecraftVersion());
        o.addProperty("loader", world.loaderName());
        o.addProperty("os", System.getProperty("os.name", ""));
        o.addProperty("rev", world.settingsRevision());
        o.add("settings", Json.COMPACT.toJsonTree(world.settings().get()));

        JsonArray cats = new JsonArray();
        for (String c : world.emojiEngine().categories()) cats.add(new com.google.gson.JsonPrimitive(c));
        o.add("categories", cats);

        JsonObject paths = new JsonObject();
        paths.addProperty("root", world.dirs().root.toString());
        paths.addProperty("cache", world.dirs().cacheDir.toString());
        paths.addProperty("logs", world.dirs().logsDir.toString());
        paths.addProperty("localLyrics", world.dirs().localLyricsDir.toString());
        o.add("paths", paths);

        o.addProperty("mediaStatus", world.media().statusLine());
        String err = world.media().errorLine();
        if (err != null) o.addProperty("mediaError", err);
        o.addProperty("spotifyStatus", world.media().spotifyAuth().status());
        o.addProperty("spotifyConnected", world.media().spotifyAuth().isConnected());
        o.addProperty("lyricsStatus", world.lyrics().status());

        PlaybackState st = world.media().current();
        if (st != null) {
            JsonObject t = new JsonObject();
            t.addProperty("title", st.title);
            t.addProperty("artist", st.artist);
            t.addProperty("album", st.album);
            t.addProperty("durationMs", st.durationMs);
            t.addProperty("positionMs", world.currentPositionMs());
            t.addProperty("playing", st.playing);
            t.addProperty("source", st.sourceName);
            o.add("track", t);
        }
        LyricsSnapshot snap = world.lyrics().snapshot();
        if (snap != null) o.add("lyrics", preview(snap.lyrics, world.currentPositionMs()));
        return o;
    }

    /** A few lines around the current one, enough for the preview panel. */
    private static JsonObject preview(Lyrics l, long pos) {
        JsonObject o = new JsonObject();
        int cur = l.lineIndexAt(pos);
        o.addProperty("current", cur);
        o.addProperty("estimated", l.hasEstimatedLines());
        JsonArray arr = new JsonArray();
        for (int i = Math.max(0, cur - 2); i <= Math.min(l.lines.size() - 1, cur + 4); i++) {
            Lyrics.Line line = l.lines.get(i);
            JsonObject lo = new JsonObject();
            lo.addProperty("i", i);
            lo.addProperty("text", line.text);
            if (i == cur && !line.isBlank()) {
                // index of the word being sung, for karaoke highlighting in the preview
                int w = 0;
                for (int k = 0; k < line.words.length; k++) if (pos >= line.words[k].startMs) w = k;
                lo.addProperty("word", w);
            }
            arr.add(lo);
        }
        o.add("lines", arr);
        return o;
    }
}
