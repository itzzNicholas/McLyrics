package dev.lyricsworld.config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;

import static org.junit.Assert.*;

public class SettingsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void sanitizeClampsEverything() {
        Settings s = new Settings();
        s.distance = 999;
        s.scale = -3;
        s.rows = 99;
        s.emojiIntensity = 500;
        s.position = "SIDEWAYS";
        s.source = "nonsense";
        s.opacity = Float.NaN;
        s.sanitize();
        assertEquals(12f, s.distance, 0f);
        assertEquals(0.25f, s.scale, 0f);
        assertEquals(7, s.rows);
        assertEquals(100, s.emojiIntensity);
        assertEquals("VIEW", s.position);
        assertEquals("AUTO", s.source);
        assertTrue(s.opacity >= 0.05f);
    }

    @Test
    public void persistsAndReloads() throws Exception {
        java.nio.file.Path f = tmp.newFolder().toPath().resolve("s.json");
        SettingsStore st = new SettingsStore(f);
        Settings s = st.get();
        s.scale = 2f;
        s.disabledEmojiCategories.add("food");
        st.update(s);
        SettingsStore again = new SettingsStore(f);
        assertEquals(2f, again.get().scale, 0f);
        assertTrue(again.get().disabledEmojiCategories.contains("food"));
    }

    @Test
    public void corruptFileFallsBackToDefaults() throws Exception {
        java.nio.file.Path f = tmp.newFolder().toPath().resolve("s.json");
        Files.write(f, "{ not json".getBytes("UTF-8"));
        assertEquals(1.0f, new SettingsStore(f).get().scale, 0f);
    }

    @Test
    public void invalidJsonUpdateIsRejected() throws Exception {
        SettingsStore st = new SettingsStore(tmp.newFolder().toPath().resolve("s.json"));
        assertFalse(st.updateFromJson("[[["));
        assertTrue(st.updateFromJson("{\"scale\":2.0}"));
        assertEquals(2f, st.get().scale, 0f);
    }

    @Test
    public void oldLayoutFilesAreMigratedOnceToTheNewDefaults() throws Exception {
        java.nio.file.Path f = tmp.newFolder().toPath().resolve("s.json");
        Files.write(f, ("{\"position\":\"BEHIND\",\"height\":2.7,\"fadeWhenMoving\":true,"
                + "\"seeThroughBlocks\":true,\"emojiIntensity\":33}").getBytes("UTF-8"));
        SettingsStore st = new SettingsStore(f);
        assertEquals("VIEW", st.get().position);
        assertEquals("ROCK", st.get().fontMode);
        assertFalse(st.get().fadeWhenMoving);
        assertFalse(st.get().seeThroughBlocks);
        assertEquals(-0.7f, st.get().height, 0.001f);
        assertEquals("unrelated options are kept", 33, st.get().emojiIntensity);
        // after migration the user's own choice sticks
        Settings s = st.get();
        s.position = "BEHIND";
        st.update(s);
        assertEquals("BEHIND", new SettingsStore(f).get().position);
    }

    @Test
    public void defaultsAreTheNewLook() {
        Settings s = new Settings().sanitize();
        assertEquals("VIEW", s.position);
        assertEquals("ROCK", s.fontMode);
        assertFalse(s.fadeWhenMoving);
        assertFalse(s.seeThroughBlocks);
        assertTrue(s.outline);
    }
}
