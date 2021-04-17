/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ilib.client.util;

import ilib.ImpLib;
import org.lwjgl.opengl.GL20;
import roj.io.IOUtil;

import java.io.IOException;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/5/27 23:37
 */
public class ShaderWrap {
    public int prog = -1;

    public void compile(String vertexPath, String fragmentPath, boolean isPath) throws Exception {
        if (this.prog != -1) {
            throw new RuntimeException("Already create!");
        }

        this.prog = GL20.glCreateProgram();

        int vertex = createShader(vertexPath, GL20.GL_VERTEX_SHADER, isPath);
        int fragment = createShader(fragmentPath, GL20.GL_FRAGMENT_SHADER, isPath);

        GL20.glLinkProgram(this.prog);

        if (GL20.glGetProgrami(this.prog, GL20.GL_LINK_STATUS) == 0) {
            throw new RuntimeException("Link shader: " + GL20.glGetProgramInfoLog(this.prog, 1024));
        }

        if (vertex != 0)  {
            GL20.glDetachShader(this.prog, vertex);
        }

        if (fragment != 0) {
            GL20.glDetachShader(this.prog, fragment);
        }

        GL20.glValidateProgram(this.prog);

        if (GL20.glGetProgrami(this.prog, GL20.GL_VALIDATE_STATUS) == 0) {
            ImpLib.logger().error("Validate shader: " + GL20.glGetProgramInfoLog(this.prog, 1024));
        }

        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
    }

    /**
     * Shared code for compiling a shader program's shader
     */
    protected int createShader(String shader, int shaderType, boolean isPath) throws IOException {
        shader = isPath ? IOUtil.readUTF(shader) : shader;
        int id = GL20.glCreateShader(shaderType);

        if (id == 0)
            throw new RuntimeException("Shader type: " + shaderType);

        GL20.glShaderSource(id, shader);
        GL20.glCompileShader(id);

        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Shader compile: " + GL20.glGetShaderInfoLog(id, 1024));
        }

        GL20.glAttachShader(this.prog, id);

        return id;
    }

    public void bind() {
        GL20.glUseProgram(this.prog);
    }

    @Override
    protected void finalize() {
        clear();
    }

    public void clear() {
        if(this.prog != -1) {
            GL20.glDeleteProgram(this.prog);
            this.prog = -1;
        }
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }
}