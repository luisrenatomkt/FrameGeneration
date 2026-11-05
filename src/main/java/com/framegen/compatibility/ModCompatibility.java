package com.framegen.compatibility;

import net.minecraftforge.fml.common.Loader;

/** Heuristicas de deteccao; nao ha garantia de compatibilidade. */
public final class ModCompatibility {
    public static boolean optifine, betterfps, patcher;
    public static void detect() {
        try { Class.forName("optifine.OptiFineForgeTweaker"); optifine = true; } catch (Throwable t) {
            try { Class.forName("Config"); optifine = true; } catch (Throwable t2) { optifine = false; }
        }
        betterfps = Loader.isModLoaded("betterfps");
        patcher = Loader.isModLoaded("patcher");
    }
    public static String summary() {
        return (optifine ? "OptiFine " : "") + (betterfps ? "BetterFPS " : "") + (patcher ? "Patcher" : "");
    }
}
