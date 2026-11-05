package com.framegen.core;

import com.framegen.compatibility.OpenGLCapabilities;
import com.framegen.config.FrameGenConfig;
import com.framegen.rendering.ShaderManager;
import com.framegen.rendering.ShaderManager.Program;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;

/**
 * Pipeline 100% em GPU (sem readback):
 *  capture()  : copia o mundo (antes do HUD) -> texCur ; reduz para luma de analise -> low[cur]
 *  estimate() : block matching low[cur] x low[prev] -> texMotion (1 vetor por bloco)
 *  drawInterpolated(t) : compoe prev/cur com compensacao de movimento direto no backbuffer
 * Tudo roda na thread de render (que possui o contexto GL).
 */
public final class FrameGenEngine {
    private int w, h, lw, lh, mw, mh;
    private int texA, texB;              // cores full-res (cur/prev alternam)
    private boolean aIsCur = true;
    private final int[] lowTex = new int[2], lowFbo = new int[2];
    private int lowIdx = 0;
    private int motionTex, motionFbo;
    private Program pCapture, pMotion, pInterp;
    private int quality = -1;            // qualidade efetiva com que os alvos foram alocados
    private float scale = 0.25f; private int block = 8, radius = 3; private float step = 3f;
    public int validFrames = 0;          // frames consecutivos capturados com sucesso
    public String status = "idle";
    private boolean ready;

    public boolean isReady() { return ready; }
    private int cur() { return aIsCur ? texA : texB; }
    private int prev() { return aIsCur ? texB : texA; }

    // ---------- recursos ----------
    public boolean ensure(int width, int height, int effQuality) {
        boolean ok = ready && width == w && height == h && effQuality == quality
                && GL11.glIsTexture(texA) && GL30.glIsFramebuffer(lowFbo[0]);
        if (ok) return true;
        destroy();
        try {
            if (pCapture == null) {
                pCapture = ShaderManager.load("fullscreen.vsh", "capture.fsh");
                pMotion = ShaderManager.load("fullscreen.vsh", "motion_estimation.fsh");
                pInterp = ShaderManager.load("fullscreen.vsh", "interpolation.fsh");
            }
            w = width; h = height; quality = effQuality;
            switch (effQuality) {
                case 0: scale = 0.25f; block = 4; radius = 2; break;
                case 1: scale = 0.5f;  block = 8; radius = 3; break;
                default: scale = 0.5f; block = 4; radius = 4; break;
            }
            lw = Math.max(16, (int) (w * scale)); lh = Math.max(16, (int) (h * scale));
            mw = Math.max(1, lw / block); mh = Math.max(1, lh / block);
            texA = newTex(w, h, GL11.GL_LINEAR);
            texB = newTex(w, h, GL11.GL_LINEAR);
            for (int i = 0; i < 2; i++) { lowTex[i] = newTex(lw, lh, GL11.GL_NEAREST); lowFbo[i] = newFbo(lowTex[i]); }
            motionTex = newTex(mw, mh, GL11.GL_LINEAR); motionFbo = newFbo(motionTex);
            ready = true; validFrames = 0; status = "ok";
            return true;
        } catch (Throwable t) {
            status = "erro: " + t.getMessage();
            com.framegen.FrameGen.LOG.error("FrameGen: falha ao criar recursos", t);
            destroyTargets(); ready = false;
            return false;
        }
    }

    private static int newTex(int tw, int th, int filter) {
        int t = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, t);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, tw, th, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return t;
    }

    private static int newFbo(int tex) {
        int f = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, f);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        int st = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (st != GL30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("FBO incompleto 0x" + Integer.toHexString(st));
        return f;
    }

    private void destroyTargets() {
        try {
            if (GL11.glIsTexture(texA)) GL11.glDeleteTextures(texA);
            if (GL11.glIsTexture(texB)) GL11.glDeleteTextures(texB);
            for (int i = 0; i < 2; i++) {
                if (GL11.glIsTexture(lowTex[i])) GL11.glDeleteTextures(lowTex[i]);
                if (GL30.glIsFramebuffer(lowFbo[i])) GL30.glDeleteFramebuffers(lowFbo[i]);
            }
            if (GL11.glIsTexture(motionTex)) GL11.glDeleteTextures(motionTex);
            if (GL30.glIsFramebuffer(motionFbo)) GL30.glDeleteFramebuffers(motionFbo);
        } catch (Throwable ignored) { /* contexto pode ter sido perdido */ }
        texA = texB = motionTex = motionFbo = 0; lowTex[0] = lowTex[1] = lowFbo[0] = lowFbo[1] = 0;
    }

    public void destroy() {
        destroyTargets(); ready = false; validFrames = 0;
        // programas sao recriados em ensure() se o contexto foi perdido
        if (pCapture != null && !GL20.glIsProgram(pCapture.id)) { pCapture = pMotion = pInterp = null; }
    }

    public void invalidateHistory() { validFrames = 0; }

    // ---------- passes ----------
    private static void quad() {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(-1, -1);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(1, -1);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(1, 1);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(-1, 1);
        GL11.glEnd();
    }

    private static void passState() {
        GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glColorMask(true, true, true, true);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static void bind(int unit, int tex) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
    }

    /** Deve ser chamado com o FBO do Minecraft vinculado e o mundo completo (sem HUD) nele. */
    public void capture(Minecraft mc) {
        int fbPrev = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            aIsCur = !aIsCur;                                   // o antigo "cur" vira "prev"
            bind(0, cur());
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h); // le do FBO vinculado

            lowIdx ^= 1;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, lowFbo[lowIdx]);
            GL11.glViewport(0, 0, lw, lh);
            passState();
            pCapture.use();
            GL20.glUniform1i(pCapture.loc("uColor"), 0);
            GL20.glUniform2f(pCapture.loc("uInvLow"), 1f / lw, 1f / lh);
            bind(0, cur());
            quad();
            validFrames++;
        } finally {
            GL20.glUseProgram(0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbPrev);
            GL11.glPopAttrib();
        }
    }

    /** Estima vetores de movimento entre prev e cur. Requer validFrames >= 2. */
    public void estimate() {
        int fbPrev = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, motionFbo);
            GL11.glViewport(0, 0, mw, mh);
            passState();
            pMotion.use();
            GL20.glUniform1i(pMotion.loc("uCurLow"), 0);
            GL20.glUniform1i(pMotion.loc("uPrevLow"), 1);
            GL20.glUniform2f(pMotion.loc("uInvLow"), 1f / lw, 1f / lh);
            GL20.glUniform1f(pMotion.loc("uBlock"), block);
            GL20.glUniform1f(pMotion.loc("uRadius"), radius);
            GL20.glUniform1f(pMotion.loc("uStep"), step);
            GL20.glUniform1f(pMotion.loc("uReach"), reach());
            GL20.glUniform1f(pMotion.loc("uLambda"), 0.004f);
            bind(1, lowTex[lowIdx ^ 1]);
            bind(0, lowTex[lowIdx]);
            quad();
        } finally {
            GL20.glUseProgram(0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbPrev);
            GL11.glPopAttrib();
        }
    }

    private float reach() { return radius * step + 1f; }

    /** Desenha o frame intermediario (t em (0,1)) no framebuffer 0, tamanho w x h. */
    public void drawInterpolated(float t) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL11.glViewport(0, 0, w, h);
            passState();
            pInterp.use();
            GL20.glUniform1i(pInterp.loc("uPrev"), 0);
            GL20.glUniform1i(pInterp.loc("uCur"), 1);
            GL20.glUniform1i(pInterp.loc("uMotion"), 2);
            GL20.glUniform2f(pInterp.loc("uInvLow"), 1f / lw, 1f / lh);
            GL20.glUniform1f(pInterp.loc("uReach"), reach());
            GL20.glUniform1f(pInterp.loc("uT"), t);
            bind(2, motionTex); bind(1, cur()); bind(0, prev());
            quad();
        } finally {
            GL20.glUseProgram(0);
            GL11.glPopAttrib();
        }
    }

    public String info() {
        return ready ? (lw + "x" + lh + " blk" + block + " r" + radius) : status;
    }
}
