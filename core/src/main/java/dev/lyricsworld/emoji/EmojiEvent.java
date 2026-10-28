package dev.lyricsworld.emoji;

/** "At {@link #timeMs} the sung word {@link #wordIndex} of line {@link #lineIndex} calls for one of these emoji." */
public final class EmojiEvent {
    public final long timeMs;
    public final int lineIndex;
    public final int wordIndex;
    public final String category;
    public final boolean main;
    public final String[] emoji;
    public final String ruleText;

    public EmojiEvent(long timeMs, int lineIndex, int wordIndex, String category, boolean main,
                      String[] emoji, String ruleText) {
        this.timeMs = timeMs;
        this.lineIndex = lineIndex;
        this.wordIndex = wordIndex;
        this.category = category;
        this.main = main;
        this.emoji = emoji;
        this.ruleText = ruleText;
    }

    @Override
    public String toString() {
        return timeMs + "ms " + category + " " + java.util.Arrays.toString(emoji) + " <- " + ruleText;
    }
}
