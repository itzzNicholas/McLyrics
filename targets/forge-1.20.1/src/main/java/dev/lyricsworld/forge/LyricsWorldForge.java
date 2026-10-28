package dev.lyricsworld.forge;

import net.minecraftforge.fml.common.Mod;

/**
 * Entry point. Deliberately empty: everything lives in {@link ClientEvents}, which Forge only
 * registers on the physical client, so a dedicated server that has this jar in its mods folder
 * never touches a single client class.
 */
@Mod(LyricsWorldForge.MOD_ID)
public final class LyricsWorldForge {
    public static final String MOD_ID = "lyricsworld";
}
