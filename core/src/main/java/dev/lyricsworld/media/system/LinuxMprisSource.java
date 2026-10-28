package dev.lyricsworld.media.system;

import dev.lyricsworld.media.PlaybackState;

import java.util.ArrayList;
import java.util.List;

/** Linux: any MPRIS2 player through the {@code playerctl} command line tool. */
final class LinuxMprisSource extends SystemMediaSource {
    private static final String SEP = "|~|";
    private static final String FORMAT = "{{playerName}}" + SEP + "{{status}}" + SEP + "{{artist}}" + SEP
            + "{{title}}" + SEP + "{{album}}" + SEP + "{{mpris:length}}" + SEP + "{{position}}";

    LinuxMprisSource(String label, String filter) { super(label, filter); }

    @Override public boolean isAvailable() {
        String out = ProcUtil.exec(2000, "playerctl", "--version");
        return out != null;
    }

    @Override
    public PlaybackState poll() {
        long t0 = System.nanoTime();
        String out = ProcUtil.exec(2500, "playerctl", "-a", "metadata", "--format", FORMAT);
        if (out == null) {
            // exit code != 0 also happens when no player is running; distinguish by --version
            error = isAvailable() ? null : "playerctl is not installed (needed for MPRIS on Linux)";
            return null;
        }
        error = null;
        long sampled = (t0 + System.nanoTime()) / 2;
        List<Candidate> list = new ArrayList<Candidate>();
        for (String line : out.split("\n")) {
            String[] f = line.split(java.util.regex.Pattern.quote(SEP), -1);
            if (f.length < 7) continue;
            list.add(new Candidate(f[0], f[3], f[2], f[4], micros(f[5]) / 1000, micros(f[6]) / 1000,
                    "Playing".equalsIgnoreCase(f[1].trim()), sampled));
        }
        return Candidate.pick(list, filter, "MPRIS");
    }

    private static long micros(String s) {
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    @Override public void close() { }
}
