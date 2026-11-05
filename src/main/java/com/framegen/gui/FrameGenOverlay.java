package com.framegen.gui;

import com.framegen.FrameGen;
import com.framegen.compatibility.OpenGLCapabilities;
import com.framegen.config.FrameGenConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Overlay com valores medidos (contadores de eventos reais em FrameScheduler). */
public final class FrameGenOverlay {
    @SubscribeEvent
    public void onText(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL || !FrameGenConfig.overlay) return;
        Minecraft mc = Minecraft.getMinecraft();
        String[] l = {
            "FRAMEGEN 1.8.9 (prototipo)",
            "Real FPS:      " + FrameGen.SCHED.realFps,
            "Generated FPS: " + FrameGen.SCHED.genFps,
            "Display FPS:   " + FrameGen.SCHED.dispFps,
            "Mode: " + FrameGenConfig.multiplier + "X  Perf: " + (FrameGenConfig.performance ? "ON" : "OFF"),
            "Estado: " + FrameGen.state,
            "Analise: " + FrameGen.ENGINE.info() + "  stutters: " + FrameGen.SCHED.stutters,
            "GL: " + OpenGLCapabilities.version
        };
        int y = 4;
        for (String s : l) { mc.fontRendererObj.drawStringWithShadow(s, 4, y, 0x55FF55); y += 10; }
    }
}
