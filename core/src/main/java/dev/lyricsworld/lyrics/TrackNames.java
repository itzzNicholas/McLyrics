package dev.lyricsworld.lyrics;

import java.text.Normalizer;
import java.util.Locale;

/** Normalisation of track metadata for lookups and cache keys. */
public final class TrackNames {
    private TrackNames() {}

    /** Removes "(feat. X)", "- Remastered 2011", "[Deluxe]" ... which break exact lyric lookups. */
    public static String cleanTitle(String title) {
        String t = title == null ? "" : title;
        t = t.replaceAll("(?i)\\s*[(\\[][^)\\]]*(feat\\.?|ft\\.?|featuring|remaster(ed)?|live|version|mono|stereo|deluxe|edit|radio|bonus)[^)\\]]*[)\\]]", "");
        t = t.replaceAll("(?i)\\s+-\\s+(\\d{4}\\s+)?(remaster(ed)?|live|mono|stereo|single version|radio edit|bonus track).*$", "");
        return t.trim();
    }

    public static String firstArtist(String artist) {
        String a = artist == null ? "" : artist;
        int cut = a.length();
        for (String sep : new String[]{",", " & ", " feat", " ft.", ";", " x "}) {
            int i = a.toLowerCase(Locale.ROOT).indexOf(sep);
            if (i > 0) cut = Math.min(cut, i);
        }
        return a.substring(0, cut).trim();
    }

    /** Stable key for caches: lower-case, accent-free, alphanumerics only. */
    public static String key(String artist, String title, long durationMs) {
        return fold(firstArtist(artist)) + "_" + fold(cleanTitle(title)) + "_" + (durationMs / 2000);
    }

    public static String fold(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        return n.replaceAll("[^\\p{L}\\p{Nd}]+", "");
    }

    /** File-system safe name, used for the user's local override files. */
    public static String fileSafe(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
