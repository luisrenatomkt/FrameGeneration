package com.framegen.core;

/** Metricas reais e pacing. Nenhum numero e multiplicado artificialmente: cada contador conta um evento ocorrido. */
public final class FrameScheduler {
    public volatile int realFps, genFps, dispFps;
    public volatile int stutters;
    private long lastPresentNs, winStart;
    private double renderNsEma;
    private int real, gen, disp;

    /** Chamado no fim de cada frame real renderizado pelo Minecraft (antes de gerar/apresentar). */
    public void onRealFrame(long now) {
        if (lastPresentNs != 0) {
            long r = now - lastPresentNs; // tempo de render puro (exclui o tempo gasto apresentando frames gerados)
            if (renderNsEma > 0 && r > renderNsEma * 2.5 && r > 40_000_000L) stutters++; // frame perdido/travada
            renderNsEma = renderNsEma == 0 ? r : renderNsEma * 0.9 + r * 0.1;
        }
        real++; disp++; // o frame real e apresentado pelo Minecraft logo apos o evento
        roll(now);
    }

    /** Chamado a cada frame gerado efetivamente apresentado via Display.update. */
    public void onGeneratedPresented() { gen++; disp++; lastPresentNs = System.nanoTime(); }

    /** Marca o fim do ciclo (ultima apresentacao nossa). */
    public void markCycleEnd() { lastPresentNs = System.nanoTime(); }

    private void roll(long now) {
        if (winStart == 0) winStart = now;
        if (now - winStart >= 1_000_000_000L) {
            realFps = real; genFps = gen; dispFps = disp;
            real = gen = disp = 0; winStart = now;
        }
    }

    /** Intervalo entre frames gerados dentro de um ciclo. Com render sincrono, o ganho maximo e limitado (ver README). */
    public long gapNs(int mult, int fpsLimit) {
        long g = (long) (renderNsEma / mult);
        if (fpsLimit > 0) g = Math.max(g, 1_000_000_000L / fpsLimit);
        return Math.min(g, 50_000_000L);
    }

    public void waitUntil(long targetNs) {
        try {
            long rem;
            while ((rem = targetNs - System.nanoTime()) > 0) {
                if (rem > 2_000_000L) Thread.sleep(1); else Thread.yield();
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public void reset() { lastPresentNs = 0; renderNsEma = 0; }
}
