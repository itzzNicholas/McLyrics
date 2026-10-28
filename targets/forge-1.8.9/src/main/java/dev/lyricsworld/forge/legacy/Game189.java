package dev.lyricsworld.forge.legacy;

import dev.lyricsworld.scene.GameAccess;
import dev.lyricsworld.scene.PlayerView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.entity.RenderManager;

/** Reads the local player/camera for the core (Minecraft 1.8.9). */
public final class Game189 implements GameAccess {
    @Override
    public boolean fillView(PlayerView out, float pt) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.theWorld == null) return false;
        out.x = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt;
        out.y = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt;
        out.z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt;
        out.yawDeg = p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * pt;
        RenderManager rm = mc.getRenderManager();
        out.camX = rm.viewerPosX;
        out.camY = rm.viewerPosY;
        out.camZ = rm.viewerPosZ;
        out.camYawDeg = rm.playerViewY;
        out.camPitchDeg = rm.playerViewX;
        out.moving = p.motionX * p.motionX + p.motionZ * p.motionZ > 0.0009;
        out.fighting = p.isSwingInProgress || p.hurtTime > 0 || mc.gameSettings.keyBindAttack.isKeyDown();
        return true;
    }
}
