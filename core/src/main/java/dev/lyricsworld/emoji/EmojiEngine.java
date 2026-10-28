package dev.lyricsworld.emoji;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lyricsworld.lyrics.Lyrics;
import dev.lyricsworld.util.Json;
import dev.lyricsworld.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Meaning -&gt; emoji engine. Rules live in JSON files ({@code assets/lyricsworld/emoji/rules_xx.json},
 * plus any {@code *.json} dropped in {@code config/lyricsworld/emoji/}), one file per language.
 *
 * Matching: single words, prefix wildcards ({@code piang*}), and phrases whose words appear in order
 * within a few tokens of each other ({@code "broke* heart"}). A negation shortly before a match
 * ("non piango", "don't cry") suppresses it. The language of every line is guessed from stop-words so
 * Italian prefixes do not fire on English lyrics and vice-versa.
 */
public final class EmojiEngine {
    /** Max number of unrelated tokens allowed between the words of a phrase. */
    static final int PHRASE_GAP = 2;
    /** How many tokens before a match a negation word may stand. */
    static final int NEGATION_WINDOW = 3;

    static final class Rule {
        final String category;
        final String[] emoji;
        final boolean main;
        final boolean negatable;
        final String text;

        Rule(String category, String[] emoji, boolean main, boolean negatable, String text) {
            this.category = category;
            this.emoji = emoji;
            this.main = main;
            this.negatable = negatable;
            this.text = text;
        }
    }

    static final class Pattern {
        final String[] tokens;
        final boolean[] prefix;
        final Rule rule;

        Pattern(String[] tokens, boolean[] prefix, Rule rule) {
            this.tokens = tokens;
            this.prefix = prefix;
            this.rule = rule;
        }

        boolean matches(int i, String tok) {
            return prefix[i] ? tok.startsWith(tokens[i]) : tok.equals(tokens[i]);
        }
    }

    static final class Language {
        final String code;
        final Set<String> negations = new HashSet<String>();
        final Set<String> stopwords = new HashSet<String>();
        final List<Pattern> patterns = new ArrayList<Pattern>();

        Language(String code) { this.code = code; }
    }

    private final Map<String, Language> languages = new LinkedHashMap<String, Language>();
    private final Set<String> categories = new TreeSet<String>();
    private int ruleCount;

    // ------------------------------------------------------------------ loading

    /** Bundled rule files plus the user's extra files; unknown emoji (not in the atlas) are dropped. */
    public static EmojiEngine loadDefault(EmojiAtlas atlas, Path userRulesDir) {
        EmojiEngine e = new EmojiEngine();
        try (InputStream in = EmojiEngine.class.getResourceAsStream("/assets/lyricsworld/emoji/languages.json")) {
            if (in != null) {
                for (JsonElement code : Json.parse(Json.readAll(in)).getAsJsonArray()) {
                    String name = "/assets/lyricsworld/emoji/rules_" + code.getAsString() + ".json";
                    try (InputStream rin = EmojiEngine.class.getResourceAsStream(name)) {
                        if (rin != null) e.addRules(Json.readAll(rin), atlas, name);
                    }
                }
            }
        } catch (IOException | RuntimeException ex) {
            Log.warn("Bundled emoji rules failed to load", ex);
        }
        if (userRulesDir != null && Files.isDirectory(userRulesDir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(userRulesDir, "*.json")) {
                for (Path p : ds) {
                    try {
                        e.addRules(new String(Files.readAllBytes(p), StandardCharsets.UTF_8), atlas, p.toString());
                    } catch (IOException | RuntimeException ex) {
                        Log.warn("Custom emoji rules ignored: " + p.getFileName(), ex);
                    }
                }
            } catch (IOException ex) {
                Log.warn("Cannot list custom emoji rules", ex);
            }
        }
        Log.info("Emoji engine: " + e.ruleCount + " rules, " + e.categories.size() + " categories, languages "
                + e.languages.keySet());
        return e;
    }

    /** Adds one rule document. {@code atlas} may be null to skip emoji validation (tests). */
    public void addRules(String json, EmojiAtlas atlas, String origin) {
        JsonObject root = Json.obj(json);
        String code = Json.str(root, "language", "xx");
        Language lang = languages.get(code);
        if (lang == null) {
            lang = new Language(code);
            languages.put(code, lang);
        }
        addAll(lang.negations, root.get("negations"));
        addAll(lang.stopwords, root.get("stopwords"));
        JsonObject cats = root.has("categories") ? root.getAsJsonObject("categories") : new JsonObject();
        int skipped = 0;
        for (Map.Entry<String, JsonElement> ce : cats.entrySet()) {
            for (JsonElement re : ce.getValue().getAsJsonArray()) {
                JsonObject r = re.getAsJsonObject();
                List<String> emoji = new ArrayList<String>();
                for (JsonElement em : r.getAsJsonArray("e")) {
                    String s = em.getAsString();
                    if (atlas == null || atlas.has(s)) emoji.add(s);
                    else skipped++;
                }
                if (emoji.isEmpty()) continue;
                List<String> pats = new ArrayList<String>();
                for (JsonElement m : r.getAsJsonArray("m")) pats.add(m.getAsString());
                boolean phrase = false;
                for (String p : pats) if (p.trim().contains(" ")) phrase = true;
                boolean main = Json.bool(r, "main", false) || phrase;
                Rule rule = new Rule(ce.getKey(), emoji.toArray(new String[0]), main,
                        Json.bool(r, "negatable", true), pats.get(0));
                for (String p : pats) {
                    String[] raw = Tokenizer.fold(p).trim().split("\\s+");
                    boolean[] prefix = new boolean[raw.length];
                    for (int i = 0; i < raw.length; i++) {
                        if (raw[i].endsWith("*")) {
                            prefix[i] = true;
                            raw[i] = raw[i].substring(0, raw[i].length() - 1);
                        }
                    }
                    if (raw.length == 0 || raw[0].isEmpty()) continue;
                    lang.patterns.add(new Pattern(raw, prefix, rule));
                }
                categories.add(ce.getKey());
                ruleCount++;
            }
        }
        if (skipped > 0) Log.warn(origin + ": " + skipped + " emoji have no picture in the atlas and were skipped");
    }

    private static void addAll(Set<String> into, JsonElement arr) {
        if (arr == null || !arr.isJsonArray()) return;
        JsonArray a = arr.getAsJsonArray();
        for (JsonElement e : a) into.add(Tokenizer.fold(e.getAsString()));
    }

    public Set<String> categories() { return Collections.unmodifiableSet(categories); }

    public int ruleCount() { return ruleCount; }

    // ------------------------------------------------------------------ analysis

    private static final class Candidate {
        final Pattern pattern;
        /** Words covered by the match (inclusive). */
        final int firstWord;
        final int lastWord;
        final int tokenCount;

        Candidate(Pattern pattern, int firstWord, int lastWord, int tokenCount) {
            this.pattern = pattern;
            this.firstWord = firstWord;
            this.lastWord = lastWord;
            this.tokenCount = tokenCount;
        }

        /** Phrases beat single words, main rules beat the rest. */
        int score() { return pattern.tokens.length * 10 + (pattern.rule.main ? 5 : 0) + Math.min(4, tokenCount); }

        boolean overlaps(Candidate o) { return firstWord <= o.lastWord && o.firstWord <= lastWord; }
    }

    /** Analyses a whole song once, up front, so playback needs no matching work at all. */
    public List<EmojiEvent> buildTimeline(Lyrics lyrics) {
        List<EmojiEvent> out = new ArrayList<EmojiEvent>();
        if (lyrics == null || languages.isEmpty()) return out;
        List<Lyrics.Line> lines = lyrics.lines;
        int[][] stopCounts = new int[lines.size()][];
        long[] totals = new long[languages.size()];
        List<Language> langList = new ArrayList<Language>(languages.values());
        List<List<Tokenizer.Token>> tokens = new ArrayList<List<Tokenizer.Token>>();
        for (int li = 0; li < lines.size(); li++) {
            Lyrics.Line line = lines.get(li);
            String[] words = new String[line.words.length];
            for (int w = 0; w < words.length; w++) words[w] = line.words[w].text;
            List<Tokenizer.Token> toks = Tokenizer.tokenize(words);
            tokens.add(toks);
            stopCounts[li] = new int[langList.size()];
            for (int k = 0; k < langList.size(); k++) {
                for (Tokenizer.Token t : toks) if (langList.get(k).stopwords.contains(t.text)) stopCounts[li][k]++;
                totals[k] += stopCounts[li][k];
            }
        }
        int songLang = 0;
        for (int k = 1; k < totals.length; k++) if (totals[k] > totals[songLang]) songLang = k;

        for (int li = 0; li < lines.size(); li++) {
            Lyrics.Line line = lines.get(li);
            if (line.isBlank()) continue;
            int best = songLang;
            for (int k = 0; k < langList.size(); k++) {
                if (stopCounts[li][k] > stopCounts[li][best]) best = k;
            }
            analyzeLine(langList.get(best), tokens.get(li), line, li, out);
        }
        Collections.sort(out, new java.util.Comparator<EmojiEvent>() {
            @Override public int compare(EmojiEvent a, EmojiEvent b) { return Long.compare(a.timeMs, b.timeMs); }
        });
        return out;
    }

    private void analyzeLine(Language lang, List<Tokenizer.Token> toks, Lyrics.Line line, int lineIndex,
                             List<EmojiEvent> out) {
        List<Candidate> found = new ArrayList<Candidate>();
        int n = toks.size();
        for (int i = 0; i < n; i++) {
            String tok = toks.get(i).text;
            for (Pattern p : lang.patterns) {
                if (!p.matches(0, tok)) continue;
                int last = i;
                boolean ok = true;
                for (int pi = 1; pi < p.tokens.length && ok; pi++) {
                    int hit = -1;
                    for (int j = last + 1; j <= Math.min(n - 1, last + 1 + PHRASE_GAP); j++) {
                        if (p.matches(pi, toks.get(j).text)) {
                            hit = j;
                            break;
                        }
                    }
                    if (hit < 0) ok = false;
                    else last = hit;
                }
                if (!ok) continue;
                if (p.rule.negatable && negated(lang, toks, i)) continue;
                found.add(new Candidate(p, toks.get(i).wordIndex, toks.get(last).wordIndex, last - i + 1));
            }
        }
        // Best matches first; anything overlapping an already accepted match (e.g. the single words
        // inside a recognised phrase) is dropped, so "broke my heart" yields one emoji, not three.
        Collections.sort(found, new java.util.Comparator<Candidate>() {
            @Override public int compare(Candidate a, Candidate b) { return Integer.compare(b.score(), a.score()); }
        });
        List<Candidate> accepted = new ArrayList<Candidate>();
        for (Candidate c : found) {
            boolean clash = false;
            for (Candidate a : accepted) if (c.overlaps(a)) { clash = true; break; }
            if (!clash) accepted.add(c);
        }
        for (Candidate c : accepted) {
            int wi = Math.min(c.lastWord, line.words.length - 1);
            out.add(new EmojiEvent(line.words[wi].startMs, lineIndex, wi, c.pattern.rule.category,
                    c.pattern.rule.main, c.pattern.rule.emoji, c.pattern.rule.text));
        }
    }

    private static boolean negated(Language lang, List<Tokenizer.Token> toks, int start) {
        for (int j = Math.max(0, start - NEGATION_WINDOW); j < start; j++) {
            if (lang.negations.contains(toks.get(j).text)) return true;
        }
        return false;
    }
}
