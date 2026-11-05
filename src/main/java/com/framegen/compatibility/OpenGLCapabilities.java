package com.framegen.compatibility;

import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

/** Deteccao de recursos. Exige GL 3.0 (FBO core do LWJGL2) + GLSL 1.20. Placas GL2.x sem FBO => desativa com seguranca. */
public final class OpenGLCapabilities {
    public static String version = "?", renderer = "?";
    public static int maxTexture = 0;
    public static boolean shaders, fbo, usable;
    public static String reason = "nao verificado";

    public static void detect() {
        ContextCapabilities c = GLContext.getCapabilities();
        version = GL11.glGetString(GL11.GL_VERSION);
        renderer = GL11.glGetString(GL11.GL_RENDERER);
        maxTexture = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        shaders = c.OpenGL20;
        fbo = c.OpenGL30;
        usable = shaders && fbo;
        reason = usable ? "ok" : (!shaders ? "sem GLSL (GL 2.0)" : "sem FBO core (GL 3.0)");
    }
}
