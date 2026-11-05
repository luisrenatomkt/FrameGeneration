package com.framegen.rendering;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.lwjgl.opengl.GL20;

/** Compila e linka programas GLSL 1.20 a partir de assets/framegen/shaders. */
public final class ShaderManager {
    public static final class Program {
        public int id;
        public int loc(String name) { return GL20.glGetUniformLocation(id, name); }
        public void use() { GL20.glUseProgram(id); }
        public void delete() { if (id != 0) GL20.glDeleteProgram(id); id = 0; }
    }

    public static Program load(String vsh, String fsh) throws IOException {
        int v = compile(GL20.GL_VERTEX_SHADER, read(vsh), vsh);
        int f = compile(GL20.GL_FRAGMENT_SHADER, read(fsh), fsh);
        Program p = new Program();
        p.id = GL20.glCreateProgram();
        GL20.glAttachShader(p.id, v);
        GL20.glAttachShader(p.id, f);
        GL20.glLinkProgram(p.id);
        GL20.glDeleteShader(v);
        GL20.glDeleteShader(f);
        if (GL20.glGetProgrami(p.id, GL20.GL_LINK_STATUS) == 0) {
            String log = GL20.glGetProgramInfoLog(p.id, 4096);
            p.delete();
            throw new IOException("link " + fsh + ": " + log);
        }
        return p;
    }

    private static int compile(int type, String src, String name) throws IOException {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(s, 4096);
            GL20.glDeleteShader(s);
            throw new IOException("compile " + name + ": " + log);
        }
        return s;
    }

    private static String read(String name) throws IOException {
        InputStream in = ShaderManager.class.getResourceAsStream("/assets/framegen/shaders/" + name);
        if (in == null) throw new FileNotFoundException(name);
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            return new String(o.toByteArray(), StandardCharsets.UTF_8);
        } finally { in.close(); }
    }
}
