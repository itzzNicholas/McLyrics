package dev.lyricsworld.lyrics;

import dev.lyricsworld.config.Settings;
import dev.lyricsworld.config.SettingsStore;
import dev.lyricsworld.emoji.EmojiEngine;
import dev.lyricsworld.emoji.EmojiEvent;
import dev.lyricsworld.media.PlaybackState;
import dev.lyricsworld.util.Dirs;
import dev.lyricsworld.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Finds lyrics for the current track off the game thread. Order: user's local .lrc override,
 * disk cache, LRCLIB. Every failure just results in a status message; nothing is thrown at the game.
 */
public final class LyricsManager {
    private final Dirs dirs;
    private final SettingsStore settings;
    private final LyricsCache cache;
    private final LrcLibClient client = new LrcLibClient();
    private final ExecutorService exec = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "LyricsWorld-lyrics");
            t.setDaemon(true);
            return t;
        }
    });

    private final EmojiEngine emojiEngine;
    private volatile LyricsSnapshot snapshot;
    private volatile String status = "Idle";
    private volatile String requestedKey = "";
    private volatile String lyricsKey = "";
    private volatile boolean lastAllowEstimate = true;

    public LyricsManager(Dirs dirs, SettingsStore settings, EmojiEngine emojiEngine) {
        this.emojiEngine = emojiEngine;
        this.dirs = dirs;
        this.settings = settings;
        this.cache = new LyricsCache(dirs.lyricsCacheDir);
    }

    /** Lyrics (+ emoji timeline) for the track last passed to {@link #onTrack}, or null while loading / unavailable. */
    public LyricsSnapshot snapshot() { return snapshot; }

    public String status() { return status; }

    /** Cheap; call every tick. Starts a lookup only when the track (or the estimate setting) changes. */
    public void onTrack(PlaybackState st) {
        Settings s = settings.get();
        if (st == null || !st.hasTrack()) {
            if (!requestedKey.isEmpty()) {
                requestedKey = "";
                snapshot = null;
                lyricsKey = "";
            }
            status = "Idle";
            return;
        }
        String key = TrackNames.key(st.artist, st.title, st.durationMs);
        if (key.equals(requestedKey) && s.allowEstimatedSync == lastAllowEstimate) return;
        requestedKey = key;
        lastAllowEstimate = s.allowEstimatedSync;
        snapshot = null;
        lyricsKey = "";
        status = "Searching lyrics...";
        final boolean allowEstimate = s.allowEstimatedSync;
        final boolean online = s.fetchLyricsOnline;
        exec.execute(new Runnable() {
            @Override public void run() { load(key, st, allowEstimate, online); }
        });
    }

    private void load(String key, PlaybackState st, boolean allowEstimate, boolean online) {
        try {
            String lrc = null;
            String source = null;

            Path local = findLocal(st);
            if (local != null) {
                lrc = new String(Files.readAllBytes(local), StandardCharsets.UTF_8);
                source = "local file " + local.getFileName();
            }
            if (lrc == null) {
                LyricsCache.Entry e = cache.get(key);
                if (e != null) {
                    if (e.found) {
                        lrc = e.lrc;
                        source = e.source + " (cached)";
                    } else if (!online) {
                        publish(key, null, "Lyrics not found");
                        return;
                    } else {
                        publish(key, null, "Lyrics not found (will retry later)");
                        return;
                    }
                }
            }
            if (lrc == null && online) {
                lrc = client.findSynced(st.artist, st.title, st.album, st.durationMs);
                if (lrc != null) {
                    source = "LRCLIB";
                    cache.putFound(key, lrc, source);
                } else {
                    cache.putMiss(key);
                }
            }
            if (lrc == null) {
                publish(key, null, online ? "Lyrics not found" : "No cached lyrics (online lookup disabled)");
                return;
            }
            Lyrics parsed = LrcParser.parse(lrc, source, allowEstimate);
            if (!parsed.hasText()) {
                publish(key, null, "Lyrics found but empty/instrumental");
                return;
            }
            publish(key, parsed, "Lyrics: " + source + (parsed.hasEstimatedLines() ? " - estimated word sync" : " - word-level sync"));
        } catch (IOException e) {
            publish(key, null, "Lyrics lookup failed: " + e.getMessage());
        } catch (RuntimeException e) {
            Log.warn("Lyrics loading error", e);
            publish(key, null, "Lyrics error: " + e.getClass().getSimpleName());
        }
    }

    private void publish(String key, Lyrics l, String msg) {
        if (!key.equals(requestedKey)) return; // track changed meanwhile
        LyricsSnapshot snap = null;
        if (l != null) {
            EmojiEvent[] ev = new EmojiEvent[0];
            if (emojiEngine != null) {
                try {
                    ev = emojiEngine.buildTimeline(l).toArray(new EmojiEvent[0]);
                } catch (RuntimeException e) {
                    Log.warn("Emoji analysis failed", e);
                }
            }
            snap = new LyricsSnapshot(l, ev);
        }
        snapshot = snap;
        lyricsKey = key;
        status = msg;
    }

    private Path findLocal(PlaybackState st) {
        try {
            String base = TrackNames.fileSafe(st.artist + " - " + st.title);
            Path p = dirs.localLyricsDir.resolve(base + ".lrc");
            if (Files.isRegularFile(p)) return p;
            String cleaned = TrackNames.fileSafe(TrackNames.firstArtist(st.artist) + " - " + TrackNames.cleanTitle(st.title));
            p = dirs.localLyricsDir.resolve(cleaned + ".lrc");
            if (Files.isRegularFile(p)) return p;
        } catch (RuntimeException ignored) { }
        return null;
    }

    public String loadedKey() { return lyricsKey; }

    public void shutdown() { exec.shutdownNow(); }
}
