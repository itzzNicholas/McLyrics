package dev.lyricsworld.mc1201;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.lyricsworld.emoji.EmojiAtlas;
import dev.lyricsworld.scene.LyricsRenderer;
import dev.lyricsworld.scene.Scene;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws a {@link Scene} with Minecraft 1.20.1's own font renderer and a textured quad per emoji.
 * Shared by the Fabric and Forge modules (both compile against Mojang names). Call {@link #begin}
 * with the level pose stack and camera, then {@link #draw}.
 */
public final class Renderer1201 implements LyricsRenderer {
    private static final ResourceLocation ATLAS = new ResourceLocation("lyricsworld", "textures/emoji_atlas.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    private final EmojiAtlas atlas = EmojiAtlas.loadDefault();
    private PoseStack poseStack;
    private Camera camera;

    public void begin(PoseStack poseStack, Camera camera) {
        this.poseStack = poseStack;
        this.camera = camera;
    }

    @Override
    public int textWidth(String styledText) {
        return Minecraft.getInstance().font.width(styledText);
    }

    @Override
    public int lineHeight() {
        return Minecraft.getInstance().font.lineHeight;
    }

    @Override
    public void draw(Scene scene) {
        if (poseStack == null || camera == null) return;
        Vec3 cam = camera.getPosition();
        if (scene.emojiCount > 0) drawEmoji(scene, cam);
        if (scene.lineCount > 0) drawText(scene, cam);
    }

    private void drawEmoji(Scene scene, Vec3 cam) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, ATLAS);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        if (scene.seeThrough) RenderSystem.disableDepthTest();

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < scene.emojiCount; i++) {
            Scene.EmojiItem e = scene.emoji[i];
            poseStack.pushPose();
            poseStack.translate(e.x - cam.x, e.y - cam.y, e.z - cam.z);
            poseStack.mulPose(camera.rotation());
            poseStack.scale(-e.size, -e.size, e.size);
            Matrix4f m = poseStack.last().pose();
            float u0 = atlas.u0(e.cell), v0 = atlas.v0(e.cell), u1 = atlas.u1(e.cell), v1 = atlas.v1(e.cell);
            buf.vertex(m, -0.5f, -0.5f, 0f).uv(u0, v0).color(1f, 1f, 1f, e.alpha).endVertex();
            buf.vertex(m, -0.5f, 0.5f, 0f).uv(u0, v1).color(1f, 1f, 1f, e.alpha).endVertex();
            buf.vertex(m, 0.5f, 0.5f, 0f).uv(u1, v1).color(1f, 1f, 1f, e.alpha).endVertex();
            buf.vertex(m, 0.5f, -0.5f, 0f).uv(u1, v0).color(1f, 1f, 1f, e.alpha).endVertex();
            poseStack.popPose();
        }
        tess.end();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void drawText(Scene scene, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Font.DisplayMode mode = scene.seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        float half = font.lineHeight / 2f;

        for (int li = 0; li < scene.lineCount; li++) {
            Scene.LineItem line = scene.lines[li];
            poseStack.pushPose();
            poseStack.translate(line.x - cam.x, line.y - cam.y, line.z - cam.z);
            poseStack.mulPose(camera.rotation());
            poseStack.scale(-line.scale, -line.scale, line.scale);
            for (int w = 0; w < line.wordCount; w++) {
                String text = line.words[w];
                float x = line.wordX[w];
                float k = line.wordScale[w];
                boolean pop = k != 1f;
                if (pop) {
                    float cx = x + font.width(text) / 2f;
                    poseStack.pushPose();
                    poseStack.translate(cx, half, 0f);
                    poseStack.scale(k, k, 1f);
                    poseStack.translate(-cx, -half, 0f);
                }
                Matrix4f m = poseStack.last().pose();
                int argb = line.wordColor[w];
                int alpha = argb >>> 24;
                if (scene.outline) {
                    int outline = (alpha << 24) | scene.outlineRgb;
                    font.drawInBatch(text, x + 1f, 0f, outline, false, m, buffers, mode, 0, FULL_BRIGHT);
                    font.drawInBatch(text, x - 1f, 0f, outline, false, m, buffers, mode, 0, FULL_BRIGHT);
                    font.drawInBatch(text, x, 1f, outline, false, m, buffers, mode, 0, FULL_BRIGHT);
                    font.drawInBatch(text, x, -1f, outline, false, m, buffers, mode, 0, FULL_BRIGHT);
                }
                font.drawInBatch(text, x, 0f, argb, scene.shadow && !scene.outline, m, buffers, mode, 0, FULL_BRIGHT);
                if (pop) poseStack.popPose();
            }
            poseStack.popPose();
        }
        buffers.endBatch();
    }
}
