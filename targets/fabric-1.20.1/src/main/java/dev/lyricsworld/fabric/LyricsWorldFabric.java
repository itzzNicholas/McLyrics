package dev.lyricsworld.fabric;

import dev.lyricsworld.mc1201.Hooks1201;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;

/** Client-only entry point: nothing is registered with, or sent to, the server. */
public final class LyricsWorldFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(Hooks1201.TOGGLE);
        KeyBindingHelper.registerKeyBinding(Hooks1201.WINDOW);
        Hooks1201.start(FabricLoader.getInstance().getGameDir(), "Fabric");
        ClientTickEvents.END_CLIENT_TICK.register(client -> Hooks1201.onClientTick());
        WorldRenderEvents.LAST.register(ctx -> Hooks1201.onRenderLevel(ctx.matrixStack(), ctx.camera(), ctx.tickDelta()));
    }
}
