package dev.lyricsworld;

import com.google.gson.JsonObject;
import dev.lyricsworld.scene.GameAccess;
import dev.lyricsworld.scene.LyricsRenderer;
import dev.lyricsworld.scene.PlayerView;
import dev.lyricsworld.scene.Scene;
import dev.lyricsworld.ui.ControlServer;
import dev.lyricsworld.util.Http;
import dev.lyricsworld.util.Json;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.*;

/** Boots the whole core with a fake game: nothing may throw, nothing may be drawn without a track. */
public class LyricsWorldSmokeTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void startsSilentlyAndServesTheControlApi() throws Exception {
        Path game = tmp.newFolder().toPath();
        Files.createDirectories(game.resolve("config/lyricsworld"));
        Files.write(game.resolve("config/lyricsworld/settings.json"),
                "{\"showExternalWindow\":false,\"source\":\"SPOTIFY\"}".getBytes("UTF-8"));

        final int[] views = {0};
        LyricsWorld w = LyricsWorld.start(game, "test-mc", "test-loader", new GameAccess() {
            @Override public boolean fillView(PlayerView out, float partial) {
                views[0]++;
                out.y = 64;
                return true;
            }
        }, new LyricsRenderer() {
            @Override public int textWidth(String s) { return s.length() * 6; }
            @Override public int lineHeight() { return 9; }
            @Override public void draw(Scene scene) { }
        });
        assertNotNull(w);
        try {
            w.clientTick();
            assertNull("no player/track -> nothing to draw", w.frame(0f));
            assertTrue(views[0] > 0);

            // --- control API ---
            ControlServer cs = controlServer(w);
            String base = "http://127.0.0.1:" + cs.port();
            HttpURLConnection noTok = (HttpURLConnection) new URL(base + "/state").openConnection();
            assertEquals(401, noTok.getResponseCode());

            JsonObject st = Json.obj(get(base + "/state", cs.token()));
            assertEquals("test-mc", Json.str(st, "minecraft", ""));
            assertTrue(st.getAsJsonArray("categories").size() >= 25);
            assertEquals("SPOTIFY", Json.str(st.getAsJsonObject("settings"), "source", ""));
            int rev = (int) Json.num(st, "rev", -1);

            HttpURLConnection post = (HttpURLConnection) new URL(base + "/settings").openConnection();
            post.setRequestMethod("POST");
            post.setRequestProperty("X-LW-Token", cs.token());
            post.setDoOutput(true);
            try (OutputStream os = post.getOutputStream()) { os.write("{\"scale\":2.0,\"rows\":5}".getBytes("UTF-8")); }
            assertEquals(200, post.getResponseCode());
            JsonObject st2 = Json.obj(get(base + "/state", cs.token()));
            assertEquals(2.0, st2.getAsJsonObject("settings").get("scale").getAsDouble(), 0);
            assertEquals(5, st2.getAsJsonObject("settings").get("rows").getAsInt());
            assertEquals(rev + 1, (int) Json.num(st2, "rev", -1));
            assertTrue("persisted", new String(Files.readAllBytes(game.resolve("config/lyricsworld/settings.json")), "UTF-8").contains("\"rows\": 5"));

            // toggle keybind path
            assertTrue(w.settings().get().enabled);
            w.toggle();
            assertFalse(w.settings().get().enabled);
            assertNull(w.frame(0f));
            w.toggle();

            // spotify login without client id is a clean error, not an exception
            HttpURLConnection login = (HttpURLConnection) new URL(base + "/spotify/login").openConnection();
            login.setRequestMethod("POST");
            login.setRequestProperty("X-LW-Token", cs.token());
            assertEquals(400, login.getResponseCode());
        } finally {
            w.shutdown();
        }
    }

    private static String get(String url, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("X-LW-Token", token);
        assertEquals(200, c.getResponseCode());
        return Http.readAll(c.getInputStream());
    }

    private static ControlServer controlServer(LyricsWorld w) throws Exception {
        Field f = LyricsWorld.class.getDeclaredField("control");
        f.setAccessible(true);
        return (ControlServer) f.get(w);
    }
}
