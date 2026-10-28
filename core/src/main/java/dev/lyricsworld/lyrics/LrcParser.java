package dev.lyricsworld.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for LRC and Enhanced LRC ("A2 extension") lyrics.
 * <pre>
 * [00:12.34] plain line
 * [00:12.34] &lt;00:12.34&gt; word &lt;00:12.80&gt; by &lt;00:13.10&gt; word &lt;00:13.50&gt;
 * [00:12.34][01:40.00] repeated line
 * [offset:+250]
 * </pre>
 * Lines without word tags get interpolated word timings via {@link #estimate}.
 */
public final class LrcParser {
    /** Longest time a single line is assumed to be on screen when no next line bounds it. */
    static final long MAX_LINE_MS = 12_000;

    private static final Pattern LINE_TAG = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD_TAG = Pattern.compile("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>");
    private static final Pattern META_TAG = Pattern.compile("^\\[([a-zA-Z#]+):(.*)]\\s*$");

    private LrcParser() {}

    private static final class RawLine {
        long start;
        String body;
    }

    public static Lyrics parse(String lrc, String source, boolean allowEstimate) {
        if (lrc == null) return new Lyrics(new ArrayList<Lyrics.Line>(), source);
        long offset = 0;
        List<RawLine> raws = new ArrayList<RawLine>();
        for (String rawLine : lrc.split("\r?\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            if (line.charAt(0) == '﻿') line = line.substring(1);
            Matcher meta = META_TAG.matcher(line);
            if (meta.matches() && !Character.isDigit(meta.group(1).charAt(0))) {
                if (meta.group(1).equalsIgnoreCase("offset")) {
                    try {
                        offset = Long.parseLong(meta.group(2).trim().replace("+", ""));
                    } catch (NumberFormatException ignored) { }
                }
                continue;
            }
            List<Long> stamps = new ArrayList<Long>();
            int pos = 0;
            Matcher m = LINE_TAG.matcher(line);
            while (m.find() && m.start() == pos) {
                stamps.add(toMs(m.group(1), m.group(2), m.group(3)));
                pos = m.end();
            }
            if (stamps.isEmpty()) continue;
            String body = line.substring(pos).trim();
            for (Long t : stamps) {
                RawLine r = new RawLine();
                r.start = t;
                r.body = body;
                raws.add(r);
            }
        }
        Collections.sort(raws, new Comparator<RawLine>() {
            @Override public int compare(RawLine a, RawLine b) { return Long.compare(a.start, b.start); }
        });

        List<Lyrics.Line> out = new ArrayList<Lyrics.Line>();
        for (int i = 0; i < raws.size(); i++) {
            RawLine r = raws.get(i);
            long start = Math.max(0, r.start - offset);
            long nextStart = i + 1 < raws.size() ? Math.max(0, raws.get(i + 1).start - offset) : Long.MAX_VALUE;
            long hardEnd = nextStart == Long.MAX_VALUE ? start + MAX_LINE_MS : nextStart;
            out.add(buildLine(start, hardEnd, r.body, offset, allowEstimate));
        }
        return new Lyrics(out, source);
    }

    private static Lyrics.Line buildLine(long start, long hardEnd, String body, long offset, boolean allowEstimate) {
        if (body.isEmpty()) return new Lyrics.Line(start, hardEnd, new Lyrics.Word[0], false);
        Matcher m = WORD_TAG.matcher(body);
        if (!m.find()) {
            String clean = body.replaceAll("\\s+", " ").trim();
            if (!allowEstimate) {
                // Keep the line whole: a single "word" that lights up as the line is sung.
                return new Lyrics.Line(start, boundedEnd(start, hardEnd, clean.length()),
                        new Lyrics.Word[]{new Lyrics.Word(clean, start, boundedEnd(start, hardEnd, clean.length()))}, true);
            }
            return estimate(start, hardEnd, clean);
        }
        // Enhanced LRC: <t> text <t> text ... [<t>]
        List<long[]> marks = new ArrayList<long[]>(); // {tagStart, tagEnd, timeMs}
        m.reset();
        while (m.find()) {
            marks.add(new long[]{m.start(), m.end(), toMs(m.group(1), m.group(2), m.group(3))});
        }
        List<Lyrics.Word> words = new ArrayList<Lyrics.Word>();
        for (int i = 0; i < marks.size(); i++) {
            long[] mk = marks.get(i);
            int segStart = (int) mk[1];
            int segEnd = i + 1 < marks.size() ? (int) marks.get(i + 1)[0] : body.length();
            String seg = body.substring(segStart, segEnd).trim();
            if (seg.isEmpty()) continue;
            long wStart = Math.max(0, mk[2] - offset);
            long wEnd = i + 1 < marks.size() ? Math.max(wStart, marks.get(i + 1)[2] - offset) : hardEnd;
            if (i + 1 >= marks.size()) wEnd = Math.min(hardEnd, Math.max(wStart + 250, wStart + seg.length() * 90L));
            String[] parts = seg.split("\\s+");
            if (parts.length == 1) {
                words.add(new Lyrics.Word(parts[0], wStart, wEnd));
            } else {
                distribute(words, parts, wStart, wEnd);
            }
        }
        if (words.isEmpty()) return new Lyrics.Line(start, hardEnd, new Lyrics.Word[0], false);
        long end = Math.max(words.get(words.size() - 1).endMs, start + 1);
        return new Lyrics.Line(Math.min(start, words.get(0).startMs), Math.min(end, Math.max(hardEnd, end)),
                words.toArray(new Lyrics.Word[0]), false);
    }

    /** Spreads the words of a line without per-word timestamps over the time the line is sung. */
    public static Lyrics.Line estimate(long start, long hardEnd, String text) {
        String[] parts = text.split("\\s+");
        long end = boundedEnd(start, hardEnd, text.length());
        List<Lyrics.Word> words = new ArrayList<Lyrics.Word>();
        distribute(words, parts, start, end);
        return new Lyrics.Line(start, hardEnd == Long.MAX_VALUE ? end : Math.max(end, Math.min(hardEnd, start + MAX_LINE_MS)),
                words.toArray(new Lyrics.Word[0]), true);
    }

    /** A line is sung for roughly its length, but never longer than the gap to the next line. */
    private static long boundedEnd(long start, long hardEnd, int chars) {
        long sing = Math.max(1200, chars * 110L);
        long gap = hardEnd == Long.MAX_VALUE ? MAX_LINE_MS : hardEnd - start;
        return start + Math.max(1, Math.min(gap, sing));
    }

    private static void distribute(List<Lyrics.Word> out, String[] parts, long start, long end) {
        double total = 0;
        double[] weight = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            weight[i] = weightOf(parts[i]);
            total += weight[i];
        }
        double t = start;
        double span = Math.max(1, end - start);
        for (int i = 0; i < parts.length; i++) {
            double d = span * weight[i] / total;
            out.add(new Lyrics.Word(parts[i], Math.round(t), Math.round(t + d)));
            t += d;
        }
    }

    /** Longer words take longer; trailing punctuation adds a small pause. */
    private static double weightOf(String w) {
        int letters = 0;
        for (int i = 0; i < w.length(); i++) if (Character.isLetterOrDigit(w.charAt(i))) letters++;
        double wgt = 1.5 + letters;
        char last = w.charAt(w.length() - 1);
        if (last == ',' || last == ';') wgt += 1.5;
        else if (last == '.' || last == '!' || last == '?') wgt += 2.5;
        return wgt;
    }

    private static long toMs(String min, String sec, String frac) {
        long ms = Long.parseLong(min) * 60_000L + Long.parseLong(sec) * 1000L;
        if (frac != null) {
            long f = Long.parseLong(frac);
            if (frac.length() == 1) f *= 100;
            else if (frac.length() == 2) f *= 10;
            ms += f;
        }
        return ms;
    }
}
