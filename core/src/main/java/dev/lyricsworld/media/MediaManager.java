package dev.lyricsworld.media;

import dev.lyricsworld.config.Settings;
import dev.lyricsworld.config.SettingsStore;
import dev.lyricsworld.media.system.SystemMediaSource;
import dev.lyricsworld.sync.SyncClock;
import dev.lyricsworld.util.Log;

/**
 * Polls the configured sources on a daemon thread and feeds the {@link SyncClock}.
 * Nothing here ever runs on the game thread.
 */
public final class MediaManager {
    private final SettingsStore settings;
    private final SpotifyAuth spotifyAuth;
    private final SpotifyWebApiSource spotifyApi;
    private final SystemMediaSource system;
    private final SyncClock clock;

    private volatile PlaybackState current;
    private volatile String statusLine = "Starting...";
    private volatile String errorLine;
    private volatile boolean running;
    private Thread thread;
    private int misses;
    private boolean lastFromApi;

    public MediaManager(SettingsStore settings, SpotifyAuth auth, SyncClock clock) {
        this.settings = settings;
        this.spotifyAuth = auth;
        this.spotifyApi = new SpotifyWebApiSource(auth);
        this.system = SystemMediaSource.create("System media session", null);
        this.clock = clock;
    }

    public PlaybackState current() { return current; }

    public String statusLine() { return statusLine; }

    public String errorLine() { return errorLine; }

    public SpotifyAuth spotifyAuth() { return spotifyAuth; }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(new Runnable() {
            @Override public void run() { loop(); }
        }, "LyricsWorld-media");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        system.close();
    }

    private void loop() {
        while (running) {
            long sleep = 1000;
            try {
                Settings s = settings.get();
                spotifyAuth.setClientId(s.spotifyClientId);
                PlaybackState st = pollOnce(s);
                if (st == null) {
                    if (++misses >= 3) { // tolerate short gaps (track switches, API hiccups)
                        current = null;
                        clock.onSample(null);
                        statusLine = errorLine != null ? "Waiting for a player..." : "Nothing playing";
                    }
                } else {
                    misses = 0;
                    current = st;
                    clock.onSample(st);
                    statusLine = (st.playing ? "Playing via " : "Paused via ") + st.sourceName;
                }
                if (lastFromApi) sleep = 1500; // be gentle with the Web API rate limit
            } catch (RuntimeException e) {
                errorLine = "Media error: " + e;
                Log.warn("Media loop error", e);
            }
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private PlaybackState pollOnce(Settings s) {
        errorLine = null;
        lastFromApi = false;
        String mode = s.source;
        PlaybackState st = null;
        boolean useApi = "SPOTIFY".equals(mode) || "AUTO".equals(mode);
        if (useApi && spotifyApi.isAvailable()) {
            lastFromApi = true;
            st = spotifyApi.poll();
            if (st != null) return st;
            lastFromApi = false;
            errorLine = spotifyApi.lastError();
            if ("SPOTIFY".equals(mode) && errorLine != null && errorLine.contains("rate")) return null;
        }
        if ("SPOTIFY".equals(mode)) system.setFilter("spotify");
        else if ("APPLE_MUSIC".equals(mode)) system.setFilter("apple");
        else system.setFilter(null);
        st = system.poll();
        if (st == null && system.lastError() != null) errorLine = system.lastError();
        return st;
    }
}
