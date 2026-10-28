package dev.lyricsworld;

import dev.lyricsworld.config.Settings;
import dev.lyricsworld.config.SettingsStore;
import dev.lyricsworld.emoji.EmojiAtlas;
import dev.lyricsworld.emoji.EmojiEngine;
import dev.lyricsworld.lyrics.LyricsManager;
import dev.lyricsworld.lyrics.LyricsSnapshot;
import dev.lyricsworld.media.MediaManager;
import dev.lyricsworld.media.PlaybackState;
import dev.lyricsworld.media.SpotifyAuth;
import dev.lyricsworld.media.SpotifyTokenStore;
import dev.lyricsworld.scene.GameAccess;
import dev.lyricsworld.scene.LyricsRenderer;
import dev.lyricsworld.scene.GlyphAtlas;
import dev.lyricsworld.scene.PlayerView;
import dev.lyricsworld.scene.Scene;
import dev.lyricsworld.scene.SceneBuilder;
import dev.lyricsworld.sync.SyncClock;
import dev.lyricsworld.ui.ControlServer;
import dev.lyricsworld.ui.UiLauncher;
import dev.lyricsworld.util.Dirs;
import dev.lyricsworld.util.Log;

import java.nio.file.Path;

/**
 * Entry point of the loader-independent mod logic. A version-specific module creates exactly one
 * instance with {@link #start}, calls {@link #clientTick()} every client tick, and {@link #frame(float)}
 * once per rendered frame (then hands the returned scene to its {@link LyricsRenderer}).
 *
 * Nothing in here talks to the server, registers anything with the game, or throws into the caller.
 */
public final class LyricsWorld {
    private static LyricsWorld instance;

    private final Dirs dirs;
    private final SettingsStore settings;
    private final SyncClock clock = new SyncClock();
    private final SpotifyAuth spotifyAuth;
    private final MediaManager media;
    private final LyricsManager lyrics;
    private final EmojiEngine emojiEngine;
    private final SceneBuilder sceneBuilder;
    private final GameAccess game;
    private final PlayerView view = new PlayerView();
    private final String minecraftVersion;
    private final String loaderName;
    private ControlServer control;
    private UiLauncher ui;
    private volatile int settingsRevision;
    private long lastErrorLogNanos;

    private LyricsWorld(Path gameDir, String minecraftVersion, String loaderName, GameAccess game, LyricsRenderer renderer) {
        this.minecraftVersion = minecraftVersion;
        this.loaderName = loaderName;
        this.game = game;
        this.dirs = new Dirs(gameDir);
        dirs.create();
        Log.init(dirs.logFile);
        Log.info("LyricsWorld " + dev.lyricsworld.util.Version.VERSION + " starting on " + loaderName + " " + minecraftVersion);
        this.settings = new SettingsStore(dirs.settingsFile);
        this.spotifyAuth = new SpotifyAuth(new SpotifyTokenStore(dirs.tokenFile));
        EmojiAtlas atlas = EmojiAtlas.loadDefault();
        this.emojiEngine = EmojiEngine.loadDefault(atlas, dirs.root.resolve("emoji"));
        this.lyrics = new LyricsManager(dirs, settings, emojiEngine);
        this.media = new MediaManager(settings, spotifyAuth, clock);
        this.sceneBuilder = new SceneBuilder(renderer, atlas, GlyphAtlas.loadDefault());
    }

    /**
     * Starts the background machinery (media polling, control server, external window).
     * Never throws; on failure the mod simply stays silent.
     */
    public static synchronized LyricsWorld start(Path gameDir, String minecraftVersion, String loaderName,
                                                 GameAccess game, LyricsRenderer renderer) {
        if (instance != null) return instance;
        try {
            LyricsWorld w = new LyricsWorld(gameDir, minecraftVersion, loaderName, game, renderer);
            w.media.start();
            w.control = new ControlServer(w);
            w.ui = new UiLauncher(w.dirs, w.control, w.settings);
            w.ui.launchIfWanted();
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override public void run() { w.shutdown(); }
            }, "LyricsWorld-shutdown"));
            instance = w;
        } catch (Throwable t) {
            Log.error("LyricsWorld failed to start - the mod stays disabled", t);
        }
        return instance;
    }

    public static LyricsWorld get() { return instance; }

    /** Cheap, call once per client tick on the game thread. */
    public void clientTick() {
        try {
            lyrics.onTrack(media.current());
        } catch (Throwable t) {
            logRateLimited("tick", t);
        }
    }

    /**
     * Compute what to draw for this frame.
     * @return the scene, or null when disabled / nothing to show / no world
     */
    public Scene frame(float partialTicks) {
        try {
            Settings s = settings.get();
            if (!s.enabled) return null;
            if (!game.fillView(view, partialTicks)) return null;
            PlaybackState st = media.current();
            long now = System.nanoTime();
            LyricsSnapshot snap = st == null ? null : lyrics.snapshot();
            long pos = clock.positionMs(now) + s.syncOffsetMs;
            Scene scene = sceneBuilder.build(view, s, snap, Math.max(0, pos), clock.isPlaying(), now);
            return scene.lineCount == 0 && scene.emojiCount == 0 ? null : scene;
        } catch (Throwable t) {
            logRateLimited("frame", t);
            return null;
        }
    }

    /** Keybind action. */
    public void toggle() {
        Settings s = settings.get();
        s.enabled = !s.enabled;
        updateSettings(s);
    }

    /** Re-opens the external settings window (it can be closed without affecting the game). */
    public void openWindow() {
        if (ui != null) ui.launch();
    }

    public void updateSettings(Settings s) {
        settings.update(s);
        settingsRevision++;
    }

    public boolean updateSettingsJson(String json) {
        boolean ok = settings.updateFromJson(json);
        if (ok) settingsRevision++;
        return ok;
    }

    public void shutdown() {
        try {
            if (ui != null) ui.stop();
            if (control != null) control.close();
            media.stop();
            lyrics.shutdown();
        } catch (Throwable ignored) {
            // JVM is going down anyway
        }
    }

    private void logRateLimited(String where, Throwable t) {
        long now = System.nanoTime();
        if (now - lastErrorLogNanos > 30_000_000_000L) {
            lastErrorLogNanos = now;
            Log.error("Error in " + where + " (further errors suppressed for 30s)", t);
        }
    }

    // accessors for the control server
    public Dirs dirs() { return dirs; }

    public SettingsStore settings() { return settings; }

    public MediaManager media() { return media; }

    public LyricsManager lyrics() { return lyrics; }

    public SyncClock clock() { return clock; }

    public EmojiEngine emojiEngine() { return emojiEngine; }

    public int settingsRevision() { return settingsRevision; }

    public String minecraftVersion() { return minecraftVersion; }

    public String loaderName() { return loaderName; }

    /** Position used for the lyrics preview in the external window. */
    public long currentPositionMs() {
        return Math.max(0, clock.positionMs(System.nanoTime()) + settings.get().syncOffsetMs);
    }
}
