package dev.lyricsworld.forge.legacy;

import dev.lyricsworld.emoji.EmojiAtlas;
import dev.lyricsworld.scene.LyricsRenderer;
import dev.lyricsworld.scene.Scene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Draws a {@link Scene} with fixed-function GL, the way Minecraft 1.8.9 draws name tags. */
public final class Renderer189 implements LyricsRenderer {
    private static final ResourceLocation ATLAS = new ResourceLocation("lyricsworld", "textures/emoji_atlas.png");
    private final EmojiAtlas atlas = EmojiAtlas.loadDefault();

    @Override
    public int textWidth(String styledText) {
        return Minecraft.getMinecraft().fontRendererObj.getStringWidth(styledText);
    }

    @Override
    public int lineHeight() {
        return Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT;
    }

    @Override
    public void draw(Scene scene) {
        Minecraft mc = Minecraft.getMinecraft();
        RenderManager rm = mc.getRenderManager();
        FontRenderer fr = mc.fontRendererObj;

        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        if (scene.seeThrough) GlStateManager.disableDepth();

        if (scene.emojiCount > 0) drawEmoji(scene, mc, rm);
        if (scene.lineCount > 0) drawText(scene, fr, rm);

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
    }

    private void billboard(RenderManager rm, double x, double y, double z, float scale) {
        GlStateManager.translate((float) (x - rm.viewerPosX), (float) (y - rm.viewerPosY), (float) (z - rm.viewerPosZ));
        GlStateManager.rotate(-rm.playerViewY, 0f, 1f, 0f);
        GlStateManager.rotate(rm.playerViewX, 1f, 0f, 0f);
        GlStateManager.scale(-scale, -scale, scale);
    }

    private void drawEmoji(Scene scene, Minecraft mc, RenderManager rm) {
        mc.getTextureManager().bindTexture(ATLAS);
        GlStateManager.enableTexture2D();
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer buf = tess.getWorldRenderer();
        for (int i = 0; i < scene.emojiCount; i++) {
            Scene.EmojiItem e = scene.emoji[i];
            GlStateManager.pushMatrix();
            billboard(rm, e.x, e.y, e.z, e.size);
            float u0 = atlas.u0(e.cell), v0 = atlas.v0(e.cell), u1 = atlas.u1(e.cell), v1 = atlas.v1(e.cell);
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            buf.pos(-0.5, -0.5, 0).tex(u0, v0).color(1f, 1f, 1f, e.alpha).endVertex();
            buf.pos(-0.5, 0.5, 0).tex(u0, v1).color(1f, 1f, 1f, e.alpha).endVertex();
            buf.pos(0.5, 0.5, 0).tex(u1, v1).color(1f, 1f, 1f, e.alpha).endVertex();
            buf.pos(0.5, -0.5, 0).tex(u1, v0).color(1f, 1f, 1f, e.alpha).endVertex();
            tess.draw();
            GlStateManager.popMatrix();
        }
    }

    private void drawText(Scene scene, FontRenderer fr, RenderManager rm) {
        float half = fr.FONT_HEIGHT / 2f;
        for (int li = 0; li < scene.lineCount; li++) {
            Scene.LineItem line = scene.lines[li];
            GlStateManager.pushMatrix();
            billboard(rm, line.x, line.y, line.z, line.scale);
            for (int w = 0; w < line.wordCount; w++) {
                String text = line.words[w];
                float x = line.wordX[w];
                float k = line.wordScale[w];
                boolean pop = k != 1f;
                if (pop) {
                    float cx = x + fr.getStringWidth(text) / 2f;
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(cx, half, 0f);
                    GlStateManager.scale(k, k, 1f);
                    GlStateManager.translate(-cx, -half, 0f);
                }
                int argb = line.wordColor[w];
                if (scene.outline) {
                    int outline = ((argb >>> 24) << 24) | scene.outlineRgb;
                    fr.drawString(text, x + 1f, 0f, outline, false);
                    fr.drawString(text, x - 1f, 0f, outline, false);
                    fr.drawString(text, x, 1f, outline, false);
                    fr.drawString(text, x, -1f, outline, false);
                }
                fr.drawString(text, x, 0f, argb, scene.shadow && !scene.outline);
                if (pop) GlStateManager.popMatrix();
            }
            GlStateManager.popMatrix();
        }
    }
}
