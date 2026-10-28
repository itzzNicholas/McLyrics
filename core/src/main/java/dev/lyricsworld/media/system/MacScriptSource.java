package dev.lyricsworld.media.system;

import dev.lyricsworld.media.PlaybackState;

import java.util.ArrayList;
import java.util.List;

/**
 * macOS: asks the scriptable players (Music = Apple Music, Spotify) through {@code osascript}.
 * Apple's private MediaRemote framework is deliberately not used: it was locked down for
 * third-party processes in macOS 15.4 and cannot be reached from plain Java anyway.
 */
final class MacScriptSource extends SystemMediaSource {
    private static final String MUSIC = ""
            + "if application \"Music\" is running then\n"
            + " tell application \"Music\"\n"
            + "  if player state is not stopped then\n"
            + "   set t to current track\n"
            + "   return \"Music\" & tab & (player state as text) & tab & (name of t) & tab & (artist of t) & tab & (album of t)"
            + " & tab & (round ((duration of t) * 1000)) & tab & (round ((player position) * 1000))\n"
            + "  end if\n"
            + " end tell\n"
            + "end if\n"
            + "return \"\"";
    private static final String SPOTIFY = ""
            + "if application \"Spotify\" is running then\n"
            + " tell application \"Spotify\"\n"
            + "  if player state is not stopped then\n"
            + "   set t to current track\n"
            + "   return \"Spotify\" & tab & (player state as text) & tab & (name of t) & tab & (artist of t) & tab & (album of t)"
            + " & tab & (duration of t) & tab & (round ((player position) * 1000))\n"
            + "  end if\n"
            + " end tell\n"
            + "end if\n"
            + "return \"\"";

    MacScriptSource(String label, String filter) { super(label, filter); }

    @Override public boolean isAvailable() { return true; }

    @Override
    public PlaybackState poll() {
        List<Candidate> list = new ArrayList<Candidate>();
        boolean wantMusic = filter == null || filter.isEmpty() || "music".contains(filter.toLowerCase())
                || filter.toLowerCase().contains("apple") || filter.toLowerCase().contains("music");
        boolean wantSpotify = filter == null || filter.isEmpty() || filter.toLowerCase().contains("spotify");
        boolean failed = false;
        if (wantMusic) failed |= !run(MUSIC, list);
        if (wantSpotify) failed |= !run(SPOTIFY, list);
        error = failed && list.isEmpty()
                ? "osascript failed - allow Minecraft/Java to control Music/Spotify in System Settings > Privacy > Automation"
                : null;
        return Candidate.pick(list, null, "macOS");
    }

    private boolean run(String script, List<Candidate> out) {
        long t0 = System.nanoTime();
        String res = ProcUtil.exec(3000, "osascript", "-e", script);
        if (res == null) return false;
        res = res.trim();
        if (res.isEmpty()) return true;
        String[] f = res.split("\t", -1);
        if (f.length < 7) return true;
        long sampled = (t0 + System.nanoTime()) / 2;
        out.add(new Candidate(f[0], f[2], f[3], f[4], num(f[5]), num(f[6]),
                "playing".equalsIgnoreCase(f[1].trim()), sampled));
        return true;
    }

    private static long num(String s) {
        try { return (long) Double.parseDouble(s.trim().replace(',', '.')); } catch (NumberFormatException e) { return 0; }
    }

    @Override public void close() { }
}
