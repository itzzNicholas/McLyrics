package dev.lyricsworld.forge.legacy;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

/**
 * Forge 1.12.2 entry point. {@code clientSideOnly} + {@code acceptableRemoteVersions = "*"} make the
 * mod invisible to the server's mod-list check: vanilla and modded servers accept clients with or without it.
 */
@Mod(modid = LyricsWorldMod.MOD_ID, name = "LyricsWorld", version = ModVersion.VERSION,
        acceptedMinecraftVersions = "[1.12.2]", clientSideOnly = true, acceptableRemoteVersions = "*")
public class LyricsWorldMod {
    public static final String MOD_ID = "lyricsworld";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new ClientHooks(Minecraft.getMinecraft().gameDir.toPath()));
    }
}
