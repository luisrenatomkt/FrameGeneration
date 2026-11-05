package com.framegen.config;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

/** Configuracao persistente (config/framegen.cfg). */
public final class FrameGenConfig {
    private static Configuration cfg;
    public static boolean enabled = false;      // desligado por padrao ate o usuario validar no hardware dele
    public static int multiplier = 2;           // 2..4
    public static boolean performance = true;
    public static int quality = 1;              // 0 baixa, 1 media, 2 alta
    public static boolean auto = false;
    public static boolean vsync = false;
    public static int fpsLimit = 0;             // 0 = sem limite para frames gerados
    public static boolean overlay = true;
    public static boolean protectHud = true;    // redesenha o HUD sobre frames gerados

    private static final String C = "general";

    public static void load(File file) {
        cfg = new Configuration(file);
        enabled = cfg.getBoolean("enabled", C, enabled, "Ativa a geracao de frames");
        multiplier = cfg.getInt("multiplier", C, multiplier, 2, 4, "2x, 3x ou 4x");
        performance = cfg.getBoolean("performance", C, performance, "Limita a analise a resolucao baixa");
        quality = cfg.getInt("quality", C, quality, 0, 2, "Qualidade da interpolacao");
        auto = cfg.getBoolean("auto", C, auto, "Ajusta qualidade automaticamente");
        vsync = cfg.getBoolean("vsync", C, vsync, "VSync aplicado na apresentacao");
        fpsLimit = cfg.getInt("fpsLimit", C, fpsLimit, 0, 1000, "Limite de FPS para frames gerados (0 = off)");
        overlay = cfg.getBoolean("overlay", C, overlay, "Mostra overlay de metricas");
        protectHud = cfg.getBoolean("protectHud", C, protectHud, "Redesenha o HUD sem interpolar");
        if (cfg.hasChanged()) cfg.save();
    }

    public static void save() {
        if (cfg == null) return;
        cfg.get(C, "enabled", false).set(enabled);
        cfg.get(C, "multiplier", 2).set(multiplier);
        cfg.get(C, "performance", true).set(performance);
        cfg.get(C, "quality", 1).set(quality);
        cfg.get(C, "auto", false).set(auto);
        cfg.get(C, "vsync", false).set(vsync);
        cfg.get(C, "fpsLimit", 0).set(fpsLimit);
        cfg.get(C, "overlay", true).set(overlay);
        cfg.get(C, "protectHud", true).set(protectHud);
        cfg.save();
    }
}
