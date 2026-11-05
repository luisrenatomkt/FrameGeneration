package com.framegen.gui;

import com.framegen.FrameGen;
import com.framegen.config.FrameGenConfig;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public final class FrameGenMenu extends GuiScreen {
    private static final String[] Q = {"Baixa", "Media", "Alta"};

    @Override public void initGui() {
        buttonList.clear();
        int x = width / 2 - 100, y = height / 6;
        String[] t = labels();
        for (int i = 0; i < t.length; i++) buttonList.add(new GuiButton(i, x, y + i * 24, 200, 20, t[i]));
        buttonList.add(new GuiButton(100, x, y + t.length * 24 + 12, 200, 20, "Concluir"));
    }

    private String[] labels() {
        return new String[] {
            "Frame Generation: " + (FrameGenConfig.enabled ? "ON" : "OFF"),
            "Multiplicador: " + FrameGenConfig.multiplier + "x",
            "Performance Mode: " + (FrameGenConfig.performance ? "ON" : "OFF"),
            "Qualidade: " + Q[FrameGenConfig.quality],
            "Modo automatico: " + (FrameGenConfig.auto ? "ON" : "OFF"),
            "VSync: " + (FrameGenConfig.vsync ? "ON" : "OFF"),
            "Limite de FPS (gerados): " + (FrameGenConfig.fpsLimit == 0 ? "sem limite" : FrameGenConfig.fpsLimit),
            "Overlay: " + (FrameGenConfig.overlay ? "ON" : "OFF"),
            "Proteger HUD: " + (FrameGenConfig.protectHud ? "ON" : "OFF")
        };
    }

    @Override protected void actionPerformed(GuiButton b) throws IOException {
        switch (b.id) {
            case 0: FrameGenConfig.enabled = !FrameGenConfig.enabled; break;
            case 1: FrameGenConfig.multiplier = FrameGenConfig.multiplier >= 4 ? 2 : FrameGenConfig.multiplier + 1; break;
            case 2: FrameGenConfig.performance = !FrameGenConfig.performance; break;
            case 3: FrameGenConfig.quality = (FrameGenConfig.quality + 1) % 3; break;
            case 4: FrameGenConfig.auto = !FrameGenConfig.auto; break;
            case 5: FrameGenConfig.vsync = !FrameGenConfig.vsync; break;
            case 6: FrameGenConfig.fpsLimit = FrameGen.nextFpsLimit(FrameGenConfig.fpsLimit); break;
            case 7: FrameGenConfig.overlay = !FrameGenConfig.overlay; break;
            case 8: FrameGenConfig.protectHud = !FrameGenConfig.protectHud; break;
            case 100: FrameGenConfig.save(); mc.displayGuiScreen(null); return;
        }
        FrameGenConfig.save();
        initGui();
    }

    @Override public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "FrameGen 1.8.9", width / 2, height / 6 - 20, 0xFFFFFF);
        super.drawScreen(mx, my, pt);
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
