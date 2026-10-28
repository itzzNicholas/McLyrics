package dev.lyricsworld.emoji;

import dev.lyricsworld.lyrics.LrcParser;
import dev.lyricsworld.lyrics.Lyrics;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class EmojiEngineTest {
    static EmojiEngine engine;
    static EmojiAtlas atlas;

    @BeforeClass
    public static void load() {
        atlas = EmojiAtlas.loadDefault();
        engine = EmojiEngine.loadDefault(atlas, null);
    }

    private static List<EmojiEvent> run(String... lines) {
        StringBuilder lrc = new StringBuilder();
        for (int i = 0; i < lines.length; i++) lrc.append(String.format("[%02d:%02d.00]%s\n", i * 5 / 60, i * 5 % 60, lines[i]));
        lrc.append("[10:00.00]end");
        Lyrics l = LrcParser.parse(lrc.toString(), "test", true);
        return engine.buildTimeline(l);
    }

    private static boolean has(List<EmojiEvent> ev, String emoji) {
        for (EmojiEvent e : ev) if (Arrays.asList(e.emoji).contains(emoji)) return true;
        return false;
    }

    @Test
    public void bundledRulesAreRichEnough() {
        assertTrue("rules: " + engine.ruleCount(), engine.ruleCount() >= 800);
        assertTrue("categories: " + engine.categories(), engine.categories().size() >= 25);
        assertTrue(engine.categories().containsAll(Arrays.asList("love", "breakup", "sadness", "fire", "water", "food",
                "animals", "money", "cars", "music", "sports", "weather", "moon", "stars", "sun", "night")));
        assertTrue(atlas.size() > 400);
    }

    @Test
    public void everyRuleEmojiHasAPictureAndRulesParse() {
        // loadDefault drops emoji without a picture; rebuild with strict checking instead
        for (String lang : new String[]{"en", "it"}) {
            EmojiEngine strict = new EmojiEngine();
            String json = readResource("/assets/lyricsworld/emoji/rules_" + lang + ".json");
            strict.addRules(json, null, lang);
            int count = strict.ruleCount();
            EmojiEngine filtered = new EmojiEngine();
            filtered.addRules(json, atlas, lang);
            assertEquals("some " + lang + " rule references an emoji missing from the atlas", count, filtered.ruleCount());
            assertTrue(count >= 400);
        }
    }

    private static String readResource(String path) {
        try (java.io.InputStream in = EmojiEngineTest.class.getResourceAsStream(path)) {
            return dev.lyricsworld.util.Json.readAll(in);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void italianBrokenHeartPhrase() {
        List<EmojiEvent> ev = run("hai rotto il mio cuore");
        assertTrue(ev.toString(), has(ev, "💔"));
    }

    @Test
    public void englishBrokenHeartPhrase() {
        assertTrue(has(run("you broke my heart"), "💔"));
        assertTrue(has(run("heartbreak tonight"), "💔"));
    }

    @Test
    public void phrasesBeatSingleWords() {
        // "heart" alone would give red hearts; the phrase must win on the same word
        List<EmojiEvent> ev = run("you broke my heart");
        assertEquals(1, ev.size());
        assertEquals("breakup", ev.get(0).category);
    }

    @Test
    public void negationSuppressesMatchesInBothLanguages() {
        assertFalse(has(run("non piango piu per te"), "😭"));
        assertFalse(has(run("i don't cry anymore"), "😭"));
        assertFalse(has(run("I can't cry"), "😭"));
        assertTrue(has(run("piango sotto la pioggia"), "😭"));
        assertTrue(has(run("I cry every night"), "😭"));
    }

    @Test
    public void inflectedItalianForms() {
        assertTrue(has(run("baciami ancora, i baci sono dolci"), "💋"));
        assertTrue(has(run("l'amore e' nell'aria"), "❤️"));
        assertTrue(has(run("stelle e luna"), "⭐") || has(run("stelle e luna"), "🌙"));
    }

    @Test
    public void accentsAreFolded() {
        assertTrue(has(run("la città è nel caffè"), "🏙️") || has(run("la città è nel caffè"), "☕"));
        assertTrue(has(run("un caffè per favore"), "☕"));
    }

    @Test
    public void languageDetectionPreventsCrossLanguageNoise() {
        // Italian rule "ball*" must not fire on an English line mentioning a ballad
        List<EmojiEvent> ev = run("this is the ballad of the lonely road and the way that we were");
        assertFalse(has(ev, "💃"));
    }

    @Test
    public void eventTimeIsTheTimeOfTheTriggerWord() {
        Lyrics l = LrcParser.parse("[00:10.00]one two three four five love six\n[00:20.00]x", "t", true);
        List<EmojiEvent> ev = engine.buildTimeline(l);
        assertFalse(ev.isEmpty());
        Lyrics.Word love = l.lines.get(0).words[5];
        assertEquals(love.startMs, ev.get(0).timeMs);
        assertEquals(5, ev.get(0).wordIndex);
    }

    @Test
    public void userRulesCanBeAdded() {
        EmojiEngine e = new EmojiEngine();
        e.addRules("{\"language\":\"en\",\"stopwords\":[\"the\"],\"categories\":{\"custom\":[{\"m\":[\"banana*\"],\"e\":[\"🍌\"]}]}}", null, "x");
        Lyrics l = LrcParser.parse("[00:01.00]the bananas\n[00:09.00]x", "t", true);
        assertEquals(1, e.buildTimeline(l).size());
        assertTrue(e.categories().contains("custom"));
    }

    @Test
    public void silenceAndGarbageAreHandled() {
        assertTrue(engine.buildTimeline(null).isEmpty());
        assertTrue(run("", "!!!", "  ").isEmpty());
        assertTrue(run("'''", "\u00a7", "l'").isEmpty());
    }

    @Test
    public void atlasKeysIgnoreVariationSelectors() {
        assertEquals(EmojiAtlas.key("❤️"), EmojiAtlas.key("❤"));
        assertEquals("1f494", EmojiAtlas.key("💔"));
        assertTrue(atlas.cellOf("💔") >= 0);
        assertEquals(-1, atlas.cellOf("not an emoji"));
    }

    @Test
    public void atlasUvAreInsideTheTexture() {
        int cell = atlas.cellOf("💔");
        assertTrue(atlas.u0(cell) > 0 && atlas.u1(cell) < 1 && atlas.u0(cell) < atlas.u1(cell));
        assertTrue(atlas.v0(cell) >= 0 && atlas.v1(cell) < 1 && atlas.v0(cell) < atlas.v1(cell));
        assertEquals(new ArrayList<String>().size(), 0);
    }
}
