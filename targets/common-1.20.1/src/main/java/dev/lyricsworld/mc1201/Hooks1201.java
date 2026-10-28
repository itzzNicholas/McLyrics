package dev.lyricsworld.mc1201;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lyricsworld.LyricsWorld;
import dev.lyricsworld.scene.Scene;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

/** Everything the Fabric and Forge entry points share: boot, tick, render, key bindings. */
public final class Hooks1201 {
    public static final String MC_VERSION = "1.20.1";
    public static final KeyMapping TOGGLE = new KeyMapping("key.lyricsworld.toggle",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.lyricsworld");
    public static final KeyMapping WINDOW = new KeyMapping("key.lyricsworld.window",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.lyricsworld");

    private static final Renderer1201 RENDERER = new Renderer1201();
    private static LyricsWorld world;

    private Hooks1201() {}

    public static void start(Path gameDir, String loaderName) {
        world = LyricsWorld.start(gameDir, MC_VERSION, loaderName, new Game1201(), RENDERER);
    }

    public static void onClientTick() {
        if (world == null) return;
        while (TOGGLE.consumeClick()) world.toggle();
        while (WINDOW.consumeClick()) world.openWindow();
        world.clientTick();
    }

    public static void onRenderLevel(PoseStack poseStack, Camera camera, float partialTick) {
        if (world == null) return;
        Scene scene = world.frame(partialTick);
        if (scene == null) return;
        RENDERER.begin(poseStack, camera);
        RENDERER.draw(scene);
    }
}
