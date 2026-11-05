package com.framegen;

import com.framegen.compatibility.ModCompatibility;
import com.framegen.compatibility.OpenGLCapabilities;
import com.framegen.config.FrameGenConfig;
import com.framegen.core.FrameGenEngine;
import com.framegen.core.FrameScheduler;
import com.framegen.gui.FrameGenMenu;
import com.framegen.gui.FrameGenOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

/**
 * Mod exclusivamente visual. Nao toca em rede, fisica, entrada ou ticks.
 * Fluxo por frame real:
 *   RenderGameOverlayEvent.Pre(ALL): mundo pronto, HUD ainda nao -> capture()
 *   RenderTickEvent END (FBO do MC ainda vinculado): estimate() + N-1 frames gerados apresentados com Display.update(false)
 *   o Minecraft entao apresenta o frame real normalmente.
 * Latencia: +1 frame real (interpolacao exige o frame futuro). Ver README.
 */
@Mod(modid = FrameGen.MODID, name = "FrameGen 1.8.9", version = "0.1.2-proto",
        acceptedMinecraftVersions = "[1.8.9]", acceptableRemoteVersions = "*")
public class FrameGen {
    public static final String MODID = "framegen";
    public static final Logger LOG = LogManager.getLogger("FrameGen");
    public static final FrameGenEngine ENGINE = new FrameGenEngine();
    public static final FrameScheduler SCHED = new FrameScheduler();

    private static final int[] FPS_LIMITS = {0, 60, 120, 144, 240};
    private final KeyBinding kToggle = new KeyBinding("Toggle FrameGen", Keyboard.KEY_F8, "FrameGen");
    private final KeyBinding kMenu = new KeyBinding("FrameGen Menu", Keyboard.KEY_F7, "FrameGen");
    private final KeyBinding kMode = new KeyBinding("Cycle 2x/3x/4x", Keyboard.KEY_F6, "FrameGen");

    private boolean capturedThisFrame, inSynthetic, glChecked, loggedFirst;
    private int appliedVsync = -1; private String lastState = "";
    private int failures, autoTick;
    private boolean permanentlyDisabled;
    private double lastX, lastY, lastZ; private float lastYaw; private int lastDim = Integer.MIN_VALUE, lastTicks;
    public static volatile String state = "OFF";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        FrameGenConfig.load(e.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        ClientRegistry.registerKeyBinding(kToggle);
        ClientRegistry.registerKeyBinding(kMenu);
        ClientRegistry.registerKeyBinding(kMode);
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(new FrameGenOverlay());
    }

    // ------------------------------------------------------------ input
    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (kToggle.isPressed()) { FrameGenConfig.enabled = !FrameGenConfig.enabled; permanentlyDisabled = false; failures = 0; FrameGenConfig.save(); }
        if (kMode.isPressed()) { FrameGenConfig.multiplier = FrameGenConfig.multiplier % 4 + 1; if (FrameGenConfig.multiplier < 2) FrameGenConfig.multiplier = 2; FrameGenConfig.save(); }
        if (kMenu.isPressed()) Minecraft.getMinecraft().displayGuiScreen(new FrameGenMenu());
    }

    public static int nextFpsLimit(int cur) {
        for (int i = 0; i < FPS_LIMITS.length; i++) if (FPS_LIMITS[i] == cur) return FPS_LIMITS[(i + 1) % FPS_LIMITS.length];
        return 0;
    }

    // ------------------------------------------------------------ gating
    private boolean allowed(Minecraft mc) {
        if (!FrameGenConfig.enabled) { state = "OFF"; return false; }
        if (permanentlyDisabled) { state = "FALLBACK (falhas)"; return false; }
        if (!glChecked) { OpenGLCapabilities.detect(); ModCompatibility.detect(); glChecked = true;
            LOG.info("GL {} / {} | usable={} ({}) | mods: {}", OpenGLCapabilities.version, OpenGLCapabilities.renderer,
                    OpenGLCapabilities.usable, OpenGLCapabilities.reason, ModCompatibility.summary()); }
        if (!OpenGLCapabilities.usable) { state = "GPU sem suporte: " + OpenGLCapabilities.reason; return false; }
        if (mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null || mc.gameSettings.hideGUI) {
            state = "PAUSADO (menu/GUI)"; return false; }
        return true;
    }

    private boolean sceneCut(Minecraft mc) {
        double dx = mc.thePlayer.posX - lastX, dy = mc.thePlayer.posY - lastY, dz = mc.thePlayer.posZ - lastZ;
        float dyaw = Math.abs(mc.thePlayer.rotationYaw - lastYaw);
        int dim = mc.theWorld.provider.getDimensionId();
        boolean cut = dx * dx + dy * dy + dz * dz > 16.0 || dyaw > 60f || dim != lastDim
                || mc.thePlayer.getHealth() <= 0f || mc.thePlayer.ticksExisted < lastTicks;
        lastX = mc.thePlayer.posX; lastY = mc.thePlayer.posY; lastZ = mc.thePlayer.posZ;
        lastYaw = mc.thePlayer.rotationYaw; lastDim = dim; lastTicks = mc.thePlayer.ticksExisted;
        return cut;
    }

    private int effectiveQuality() {
        int q = FrameGenConfig.quality;
        if (FrameGenConfig.performance) q = Math.min(q, 0);
        return q;
    }

    // ------------------------------------------------------------ captura (mundo sem HUD)
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onOverlayPre(RenderGameOverlayEvent.Pre e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL || inSynthetic) return;
        Minecraft mc = Minecraft.getMinecraft();
        capturedThisFrame = false;
        if (!allowed(mc)) { ENGINE.invalidateHistory(); return; }
        try {
            if (!ENGINE.ensure(mc.displayWidth, mc.displayHeight, effectiveQuality())) { fail(); return; }
            ENGINE.capture(mc);
            capturedThisFrame = true;
        } catch (Throwable t) { LOG.error("captura falhou", t); fail(); }
    }

    // ------------------------------------------------------------ geracao + apresentacao
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!state.equals(lastState)) { lastState = state; LOG.info("estado: {}", state); }
        SCHED.onRealFrame(System.nanoTime());
        boolean cap = capturedThisFrame; capturedThisFrame = false;
        if (!cap || mc.theWorld == null || mc.thePlayer == null) { SCHED.markCycleEnd(); return; }
        if (sceneCut(mc)) { ENGINE.invalidateHistory(); state = "CORTE DE CENA"; SCHED.markCycleEnd(); return; }
        if (ENGINE.validFrames < 2) { state = "AQUECENDO"; SCHED.markCycleEnd(); return; }
        if (mc.displayWidth != Display.getWidth() || mc.displayHeight != Display.getHeight()) { SCHED.markCycleEnd(); return; }
        try {
            applyVsync();
            drainGlErrors("antes do pipeline (erro pendente de outro mod/OptiFine)");
            ENGINE.estimate();
            int m = FrameGenConfig.multiplier;
            long gap = SCHED.gapNs(m, FrameGenConfig.fpsLimit);
            for (int k = 1; k < m; k++) {
                SCHED.waitUntil(System.nanoTime() + gap);
                ENGINE.drawInterpolated(k / (float) m);
                drawHud(mc, e.renderTickTime);
                Display.update(false);            // troca de buffers real; false = nao reprocessa entrada
                SCHED.onGeneratedPresented();
            }
            SCHED.markCycleEnd();
            // restaura o FBO do Minecraft para o resto do ciclo vanilla (que o desvincula e apresenta o frame real)
            mc.getFramebuffer().bindFramebuffer(false);
            drainGlErrors("apos o pipeline (erro do FrameGen)");
            failures = 0; state = "ATIVO " + m + "x";
            if (!loggedFirst) { loggedFirst = true; LOG.info("Primeiro frame gerado OK: {} {}x gap={}us", ENGINE.info(), m, gap / 1000); }
            autoQuality();
        } catch (Throwable t) {
            LOG.error("geracao falhou (glError=" + org.lwjgl.opengl.GL11.glGetError() + ")", t);
            try { mc.getFramebuffer().bindFramebuffer(false); } catch (Throwable ignored) { }
            fail();
        }
    }

    private int glErrLogged;
    /** Esvazia a fila de erros GL. Erros pendentes de outros mods sao so registrados (max 5), nunca derrubam a geracao. */
    private void drainGlErrors(String where) {
        int e, n = 0;
        while ((e = org.lwjgl.opengl.GL11.glGetError()) != 0 && n < 16) {
            n++;
            if (glErrLogged < 5) { glErrLogged++; LOG.warn("GL error 0x{} {}", Integer.toHexString(e), where); }
        }
    }

    private void drawHud(Minecraft mc, float pt) {
        if (!FrameGenConfig.protectHud) return;
        inSynthetic = true;
        try {
            mc.entityRenderer.setupOverlayRendering();
            mc.ingameGUI.renderGameOverlay(pt);
        } finally { inSynthetic = false; }
    }

    private void applyVsync() {
        int want = FrameGenConfig.vsync ? 1 : 0;
        if (appliedVsync != want) { Display.setVSyncEnabled(FrameGenConfig.vsync); appliedVsync = want; LOG.info("VSync -> {}", FrameGenConfig.vsync); }
    }

    /** Modo automatico: baixa qualidade se a taxa apresentada ficar bem abaixo da meta; sobe se houver folga. */
    private void autoQuality() {
        if (!FrameGenConfig.auto || ++autoTick < 120) return;
        autoTick = 0;
        int real = SCHED.realFps, disp = SCHED.dispFps, target = real * FrameGenConfig.multiplier;
        if (real <= 0) return;
        if (disp < target * 0.8 && FrameGenConfig.quality > 0) FrameGenConfig.quality--;
        else if (disp > target * 0.95 && FrameGenConfig.quality < 2 && !FrameGenConfig.performance) FrameGenConfig.quality++;
    }

    private void fail() {
        ENGINE.invalidateHistory();
        if (++failures >= 3) {
            permanentlyDisabled = true;   // volta ao render original do Minecraft (nunca tela preta por nossa culpa)
            LOG.error("FrameGen desativado apos 3 falhas consecutivas. Reative com a tecla de toggle.");
        }
    }
}
