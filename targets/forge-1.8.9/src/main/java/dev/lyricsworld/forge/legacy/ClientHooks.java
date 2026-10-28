package dev.lyricsworld.forge.legacy;

import dev.lyricsworld.LyricsWorld;
import dev.lyricsworld.scene.Scene;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.nio.file.Path;

/** Tick + render hooks and key bindings for Forge 1.8.9. */
public final class ClientHooks {
    private final KeyBinding toggle = new KeyBinding("key.lyricsworld.toggle", Keyboard.KEY_J, "key.categories.lyricsworld");
    private final KeyBinding window = new KeyBinding("key.lyricsworld.window", Keyboard.KEY_NONE, "key.categories.lyricsworld");
    private final Renderer189 renderer = new Renderer189();
    private final LyricsWorld world;

    public ClientHooks(Path gameDir) {
        ClientRegistry.registerKeyBinding(toggle);
        ClientRegistry.registerKeyBinding(window);
        this.world = LyricsWorld.start(gameDir, "1.8.9", "Forge", new Game189(), renderer);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (world == null || event.phase != TickEvent.Phase.END) return;
        while (toggle.isPressed()) world.toggle();
        while (window.isPressed()) world.openWindow();
        world.clientTick();
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (world == null) return;
        Scene scene = world.frame(event.partialTicks);
        if (scene != null) renderer.draw(scene);
    }
}
