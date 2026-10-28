package dev.lyricsworld.mc1201;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lyricsworld.emoji.EmojiAtlas;
import dev.lyricsworld.scene.GlyphAtlas;
import dev.lyricsworld.scene.LyricsRenderer;
import dev.lyricsworld.scene.Scene;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws a {@link Scene} on Minecraft 1.20.1. Shared by the Fabric and Forge modules (Mojang names).
 * Everything goes through the vanilla buffer source with text-shader render types, so shader packs
 * treat it like regular text. Call {@link #begin} with the level pose stack and camera, then {@link #draw}.
 */
public final class Renderer1201 implements LyricsRenderer {
    private static final ResourceLocation EMOJI_ATLAS = new ResourceLocation("lyricsworld", "textures/emoji_atlas.png");
    private static final ResourceLocation FONT_ATLAS = new ResourceLocation("lyricsworld", "textures/font_atlas.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    private final EmojiAtlas atlas = EmojiAtlas.loadDefault();
    private final QuadWriter quads = new QuadWriter();
    private PoseStack poseStack;
    private Camera camera;

    /** Writes glyph quads (line-local pixel coordinates) into a vertex consumer. No allocation per quad. */
    private static final class QuadWriter implements GlyphAtlas.QuadSink {
        VertexConsumer vc;
        Matrix4f m;

        @Override
        public void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                         float u0, float v0, float u1, float v1, int argb) {
            int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
            vc.vertex(m, x0, y0, 0f).color(r, g, b, a).uv(u0, v0).uv2(FULL_BRIGHT).endVertex();
            vc.vertex(m, x1, y1, 0f).color(r, g, b, a).uv(u0, v1).uv2(FULL_BRIGHT).endVertex();
            vc.vertex(m, x2, y2, 0f).color(r, g, b, a).uv(u1, v1).uv2(FULL_BRIGHT).endVertex();
            vc.vertex(m, x3, y3, 0f).color(r, g, b, a).uv(u1, v0).uv2(FULL_BRIGHT).endVertex();
        }
    }

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
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        if (scene.emojiCount > 0) drawEmoji(scene, cam, buffers);
        if (scene.lineCount > 0) {
            if (scene.customFont && scene.glyphs != null) drawGlyphLines(scene, cam, buffers);
            else drawGameFont(scene, cam, buffers);
        }
        buffers.endBatch();
    }

    private void billboard(Vec3 cam, double x, double y, double z, float scale) {
        poseStack.translate(x - cam.x, y - cam.y, z - cam.z);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(-scale, -scale, scale);
    }

    private void drawEmoji(Scene scene, Vec3 cam, MultiBufferSource.BufferSource buffers) {
        RenderType rt = LyricsRenderTypes.get(EMOJI_ATLAS, scene.seeThrough);
        VertexConsumer vc = buffers.getBuffer(rt);
        for (int i = 0; i < scene.emojiCount; i++) {
            Scene.EmojiItem e = scene.emoji[i];
            poseStack.pushPose();
            billboard(cam, e.x, e.y, e.z, e.size);
            Matrix4f m = poseStack.last().pose();
            float u0 = atlas.u0(e.cell), v0 = atlas.v0(e.cell), u1 = atlas.u1(e.cell), v1 = atlas.v1(e.cell);
            int a = Math.round(Math.max(0f, Math.min(1f, e.alpha)) * 255f);
            vc.vertex(m, -0.5f, -0.5f, 0f).color(255, 255, 255, a).uv(u0, v0).uv2(FULL_BRIGHT).endVertex();
            vc.vertex(m, -0.5f, 0.5f, 0f).color(255, 255, 255, a).uv(u0, v1).uv2(FULL_BRIGHT).endVertex();
            vc.vertex(m, 0.5f, 0.5f, 0f).color(255, 255, 255, a).uv(u1, v1).uv2(FULL_BRIGHT).endVertex();
            vc.vertex(m, 0.5f, -0.5f, 0f).color(255, 255, 255, a).uv(u1, v0).uv2(FULL_BRIGHT).endVertex();
            poseStack.popPose();
        }
        buffers.endBatch(rt);
    }

    /** The bundled Rock Salt font: outline pass for every line first, then the coloured fill. */
    private void drawGlyphLines(Scene scene, Vec3 cam, MultiBufferSource.BufferSource buffers) {
        RenderType rt = LyricsRenderTypes.get(FONT_ATLAS, scene.seeThrough);
        quads.vc = buffers.getBuffer(rt);
        for (int pass = scene.outline ? 0 : 1; pass < 2; pass++) {
            for (int li = 0; li < scene.lineCount; li++) {
                Scene.LineItem line = scene.lines[li];
                poseStack.pushPose();
                billboard(cam, line.x, line.y, line.z, line.scale);
                quads.m = poseStack.last().pose();
                scene.glyphs.emitLine(line, pass == 0, scene.outlineRgb, quads);
                poseStack.popPose();
            }
        }
        buffers.endBatch(rt);
    }

    /** Fallback: Minecraft's own font (fontMode = GAME). */
    private void drawGameFont(Scene scene, Vec3 cam, MultiBufferSource.BufferSource buffers) {
        Font font = Minecraft.getInstance().font;
        Font.DisplayMode mode = scene.seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        float half = font.lineHeight / 2f;
        for (int li = 0; li < scene.lineCount; li++) {
            Scene.LineItem line = scene.lines[li];
            poseStack.pushPose();
            billboard(cam, line.x, line.y, line.z, line.scale);
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
                if (scene.outline) {
                    int outline = ((argb >>> 24) << 24) | scene.outlineRgb;
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
    }
}
