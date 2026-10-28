package dev.lyricsworld.scene;

/** What the core needs to know about the running game. One implementation per Minecraft version. */
public interface GameAccess {
    /** Fill {@code out} with the local player's state; leave {@code out} untouched and return false if there is no world. */
    boolean fillView(PlayerView out, float partialTicks);
}
