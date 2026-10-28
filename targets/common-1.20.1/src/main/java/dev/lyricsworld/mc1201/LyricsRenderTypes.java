package dev.lyricsworld.mc1201;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Render types for the lyrics font and the emoji atlas. They use vanilla's own *text* shader, vertex
 * format and lightmap handling (the same as name tags), which is what shader packs (Iris/Oculus) hook,
 * so the lyrics get lit and processed by the pack like any other text. Unlike {@code RenderType.text}
 * the texture is sampled with linear filtering, so scaled glyphs/emoji stay smooth.
 *
 * Extends RenderType only to reach its protected render-state constants.
 */
public final class LyricsRenderTypes extends RenderType {
    private static final Map<String, RenderType> CACHE = new HashMap<String, RenderType>();

    private LyricsRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                              boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        throw new IllegalStateException("not instantiable");
    }

    /** @param seeThrough true = ignore the depth buffer (visible through blocks); false = blends with the scene */
    public static synchronized RenderType get(ResourceLocation texture, boolean seeThrough) {
        String key = texture + (seeThrough ? "#see" : "#depth");
        RenderType rt = CACHE.get(key);
        if (rt == null) {
            RenderType.CompositeState.CompositeStateBuilder b = RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_TEXT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setCullState(NO_CULL);
            if (seeThrough) {
                b.setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE);
            } else {
                b.setDepthTestState(LEQUAL_DEPTH_TEST);
            }
            rt = RenderType.create("lyricsworld_" + (seeThrough ? "see_" : "depth_") + texture.getPath(),
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 4096,
                    false, false, b.createCompositeState(false));
            CACHE.put(key, rt);
        }
        return rt;
    }
}
