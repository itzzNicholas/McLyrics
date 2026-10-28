package dev.lyricsworld.mc1201;

import dev.lyricsworld.scene.GameAccess;
import dev.lyricsworld.scene.PlayerView;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Reads the local player/camera for the core (Minecraft 1.20.1, Mojang mappings). */
public final class Game1201 implements GameAccess {
    @Override
    public boolean fillView(PlayerView out, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return false;
        out.x = Mth.lerp((double) partialTicks, p.xo, p.getX());
        out.y = Mth.lerp((double) partialTicks, p.yo, p.getY());
        out.z = Mth.lerp((double) partialTicks, p.zo, p.getZ());
        out.yawDeg = p.getViewYRot(partialTicks);
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 cp = cam.getPosition();
        out.camX = cp.x;
        out.camY = cp.y;
        out.camZ = cp.z;
        out.camYawDeg = cam.getYRot();
        out.moving = p.getDeltaMovement().horizontalDistanceSqr() > 0.0009;
        out.fighting = p.swinging || p.hurtTime > 0 || mc.options.keyAttack.isDown();
        return true;
    }
}
