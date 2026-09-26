package com.cardboardstereo;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Minimal GLSL program wrapper used for the barrel-distortion (fisheye)
 * compositing pass. Uses plain GL20 calls rather than Minecraft's internal
 * shader-manager classes, since those are more likely to differ across MCP
 * mapping versions and we only need a trivial full-screen-quad shader.
 */
public class ShaderProgram {

    private int programId = -1;
    private int vertexId = -1;
    private int fragmentId = -1;
    private boolean valid = false;

    public void load(String vertexResourcePath, String fragmentResourcePath) {
        try {
            String vertexSource = readResource(vertexResourcePath);
            String fragmentSource = readResource(fragmentResourcePath);

            vertexId = compile(GL20.GL_VERTEX_SHADER, vertexSource);
            fragmentId = compile(GL20.GL_FRAGMENT_SHADER, fragmentSource);

            programId = GL20.glCreateProgram();
            GL20.glAttachShader(programId, vertexId);
            GL20.glAttachShader(programId, fragmentId);
            GL20.glLinkProgram(programId);

            int linked = GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS);
            if (linked == GL11.GL_FALSE) {
                String log = GL20.glGetProgramInfoLog(programId, 4096);
                throw new RuntimeException("CardboardStereo: shader link failed: " + log);
            }

            valid = true;
        } catch (Exception e) {
            valid = false;
            System.err.println("CardboardStereo: failed to load fisheye shader, "
                + "distortion will be disabled. Cause: " + e);
        }
    }

    private int compile(int type, String source) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, source);
        GL20.glCompileShader(id);

        int compiled = GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS);
        if (compiled == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(id, 4096);
            throw new RuntimeException("CardboardStereo: shader compile failed ("
                + (type == GL20.GL_VERTEX_SHADER ? "vertex" : "fragment") + "): " + log);
        }
        return id;
    }

    private String readResource(String path) throws IOException {
        InputStream in = ShaderProgram.class.getResourceAsStream(path);
        if (in == null) {
            throw new IOException("Shader resource not found on classpath: " + path);
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    public boolean isValid() {
        return valid;
    }

    public void use() {
        if (valid) {
            GL20.glUseProgram(programId);
        }
    }

    public static void unuse() {
        GL20.glUseProgram(0);
    }

    public void setUniform1f(String name, float value) {
        if (!valid) return;
        int loc = GL20.glGetUniformLocation(programId, name);
        if (loc >= 0) {
            GL20.glUniform1f(loc, value);
        }
    }

    public void setUniform1i(String name, int value) {
        if (!valid) return;
        int loc = GL20.glGetUniformLocation(programId, name);
        if (loc >= 0) {
            GL20.glUniform1i(loc, value);
        }
    }
}
