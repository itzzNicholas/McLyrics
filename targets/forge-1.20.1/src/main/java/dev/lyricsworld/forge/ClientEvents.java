package dev.lyricsworld.forge;

import dev.lyricsworld.mc1201.Hooks1201;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-side wiring for Forge 1.20.1. No network code, no registries. */
public final class ClientEvents {
    private ClientEvents() {}

    @Mod.EventBusSubscriber(modid = LyricsWorldForge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(Hooks1201.TOGGLE);
            event.register(Hooks1201.WINDOW);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            Hooks1201.start(Minecraft.getInstance().gameDirectory.toPath(), "Forge");
        }
    }

    @Mod.EventBusSubscriber(modid = LyricsWorldForge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {}

        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) Hooks1201.onClientTick();
        }

        @SubscribeEvent
        public static void onRender(RenderLevelStageEvent event) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                Hooks1201.onRenderLevel(event.getPoseStack(), event.getCamera(), event.getPartialTick());
            }
        }
    }
}
