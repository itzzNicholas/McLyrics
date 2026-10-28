package dev.lyricsworld.forge.legacy;

import dev.lyricsworld.emoji.EmojiAtlas;
import dev.lyricsworld.scene.GlyphAtlas;
import dev.lyricsworld.scene.LyricsRenderer;
import dev.lyricsworld.scene.Scene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Draws a {@link Scene} with fixed-function GL, the way Minecraft 1.12.2 draws name tags. */
public final class Renderer112 implements LyricsRenderer {
    private static final ResourceLocation ATLAS = new ResourceLocation("lyricsworld", "textures/emoji_atlas.png");
    private static final ResourceLocation FONT_ATLAS = new ResourceLocation("lyricsworld", "textures/font_atlas.png");
    private final EmojiAtlas atlas = EmojiAtlas.loadDefault();
    private final QuadWriter quads = new QuadWriter();

    /** Writes glyph quads (line-local pixel coordinates) into the tessellator buffer. */
    private static final class QuadWriter implements GlyphAtlas.QuadSink {
        BufferBuilder buf;

        @Override
        public void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                         float u0, float v0, float u1, float v1, int argb) {
            int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
            buf.pos(x0, y0, 0).tex(u0, v0).color(r, g, b, a).endVertex();
            buf.pos(x1, y1, 0).tex(u0, v1).color(r, g, b, a).endVertex();
            buf.pos(x2, y2, 0).tex(u1, v1).color(r, g, b, a).endVertex();
            buf.pos(x3, y3, 0).tex(u1, v0).color(r, g, b, a).endVertex();
        }
    }

    @Override
    public int textWidth(String styledText) {
        return Minecraft.getMinecraft().fontRenderer.getStringWidth(styledText);
    }

    @Override
    public int lineHeight() {
        return Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT;
    }

    @Override
    public void draw(Scene scene) {
        Minecraft mc = Minecraft.getMinecraft();
        RenderManager rm = mc.getRenderManager();
        FontRenderer fr = mc.fontRenderer;

        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        if (scene.seeThrough) GlStateManager.disableDepth();

        if (scene.emojiCount > 0) drawEmoji(scene, mc, rm);
        if (scene.lineCount > 0) {
            if (scene.customFont && scene.glyphs != null) drawGlyphs(scene, mc, rm);
            else drawText(scene, fr, rm);
        }

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
        BufferBuilder buf = tess.getBuffer();
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

    /** The bundled Rock Salt font: outline pass for every line first, then the coloured fill. */
    private void drawGlyphs(Scene scene, Minecraft mc, RenderManager rm) {
        mc.getTextureManager().bindTexture(FONT_ATLAS);
        GlStateManager.enableTexture2D();
        Tessellator tess = Tessellator.getInstance();
        quads.buf = tess.getBuffer();
        for (int pass = scene.outline ? 0 : 1; pass < 2; pass++) {
            for (int li = 0; li < scene.lineCount; li++) {
                Scene.LineItem line = scene.lines[li];
                GlStateManager.pushMatrix();
                billboard(rm, line.x, line.y, line.z, line.scale);
                quads.buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
                scene.glyphs.emitLine(line, pass == 0, scene.outlineRgb, quads);
                tess.draw();
                GlStateManager.popMatrix();
            }
        }
    }

    /** Fallback: Minecraft's own font (fontMode = GAME). */
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
