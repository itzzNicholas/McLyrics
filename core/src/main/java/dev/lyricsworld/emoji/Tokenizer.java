package dev.lyricsworld.emoji;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Splits lyric words into lower-case, accent-free match tokens. */
final class Tokenizer {
    static final class Token {
        final String text;
        final int wordIndex;

        Token(String text, int wordIndex) {
            this.text = text;
            this.wordIndex = wordIndex;
        }
    }

    private Tokenizer() {}

    /** Lower-case, strip diacritics, unify apostrophes. */
    static String fold(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replace('’', '\'').replace('‘', '\'').replace('`', '\'');
    }

    /**
     * English contractions ("don't" -&gt; "dont", "you're" -&gt; "youre") keep their letters so they can
     * match negation lists; elisions ("l'amore", "dell'anima") keep only the word after the apostrophe.
     */
    static List<Token> tokenize(String[] words) {
        List<Token> out = new ArrayList<Token>();
        for (int w = 0; w < words.length; w++) {
            String folded = fold(words[w]);
            StringBuilder cur = new StringBuilder();
            List<String> parts = new ArrayList<String>();
            for (int i = 0; i < folded.length(); i++) {
                char ch = folded.charAt(i);
                if (Character.isLetterOrDigit(ch) || ch == '\'') {
                    cur.append(ch);
                } else if (cur.length() > 0) {
                    parts.add(cur.toString());
                    cur.setLength(0);
                }
            }
            if (cur.length() > 0) parts.add(cur.toString());
            for (String p : parts) {
                for (String t : splitApostrophe(p)) {
                    if (!t.isEmpty()) out.add(new Token(t, w));
                }
            }
        }
        return out;
    }

    private static List<String> splitApostrophe(String p) {
        List<String> r = new ArrayList<String>(2);
        String stripped = p;
        while (stripped.startsWith("'")) stripped = stripped.substring(1);
        while (stripped.endsWith("'")) stripped = stripped.substring(0, stripped.length() - 1);
        int i = stripped.indexOf('\'');
        if (i < 0) {
            r.add(stripped);
            return r;
        }
        String before = stripped.substring(0, i);
        String after = stripped.substring(i + 1).replace("'", "");
        if (isEnglishContractionTail(after)) {
            r.add(before + after);
        } else {
            r.add(after); // Italian/French style elision: l'amore -> amore
        }
        return r;
    }

    private static boolean isEnglishContractionTail(String after) {
        return after.equals("t") || after.equals("s") || after.equals("re") || after.equals("ll")
                || after.equals("ve") || after.equals("d") || after.equals("m") || after.equals("em");
    }
}
