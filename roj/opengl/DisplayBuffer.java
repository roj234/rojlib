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
package roj.opengl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import roj.opengl.util.VboUtil;
import roj.opengl.vertex.VertexBuilder;
import roj.opengl.vertex.VertexFormat;

import java.util.function.Consumer;

import static roj.opengl.util.VboUtil.vboSupported;
import static roj.opengl.vertex.VertexFormat.Entry;

/**
 * Display Buffer
 *
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class DisplayBuffer {
    private int bufferId = -1, listId = -1;
    private final VertexFormat            vertexFormat;
    private final Consumer<VertexBuilder> drawer;
    private final VertexBuilder           vertexBuilder;

    protected DisplayBuffer(VertexFormat format, VertexBuilder builder) {
        this.vertexFormat = format;
        vertexBuilder = builder;
        this.drawer = null;
    }

    public DisplayBuffer(VertexFormat format, Consumer<VertexBuilder> drawer, VertexBuilder builder) {
        this.vertexFormat = format;
        this.drawer = drawer;
        vertexBuilder = builder;
    }

    protected void drawInternal(VertexBuilder vb) {
        drawer.accept(vb);
    }

    public final void firstDraw(int mode) {
        firstDraw(mode, true);
    }

    public void firstDraw(int mode, boolean compileOnly) {
        if (this.listId < 0) {
            if((this.listId = GL11.glGenLists(1)) == 0) {
                int id = GL11.glGetError();
                String msg;
                if (id != 0) {
                    msg = GLU.gluErrorString(id);
                } else {
                    msg = "Unknown";
                }
                throw new IllegalStateException("Failed to generate list: " + msg);
            }
        }
        if (vboSupported) {
            if (this.bufferId < 0) {
                this.bufferId = VboUtil.glGenBuffers();
            }
            VboUtil.glBindBuffer(VboUtil.GL_ARRAY_BUFFER, this.bufferId);
        } else {
            GL11.glNewList(this.listId, compileOnly ? GL11.GL_COMPILE : GL11.GL_COMPILE_AND_EXECUTE);
        }

        VertexBuilder vb = vertexBuilder;

        vb.begin(vertexFormat);

        drawInternal(vb);

        vb.end();
        if (vboSupported) {
            VboUtil.glBufferData(VboUtil.GL_ARRAY_BUFFER, vb.getBuffer(), VboUtil.GL_STATIC_DRAW);
            VboUtil.glBindBuffer(VboUtil.GL_ARRAY_BUFFER, 0);
            int vertexCount = vb.getVertexCount();

            GL11.glNewList(this.listId, GL11.GL_COMPILE);

            VboUtil.glBindBuffer(VboUtil.GL_ARRAY_BUFFER, this.bufferId);

            VertexFormat.Entry[] list = vertexFormat.entries();

            int size = vertexFormat.getSize();
            for (Entry value : list) {
                VboUtil.preDrawVBO(value, size);
            }

            GL11.glDrawArrays(mode, 0, vertexCount);
            VboUtil.glBindBuffer(VboUtil.GL_ARRAY_BUFFER, 0);

            for (Entry entry : list) {
                VboUtil.postDraw(entry);
            }
        } else {
            VboUtil.drawVertexes(mode, vb);
        }
        GL11.glEndList();
    }

    public void draw() {
        GL11.glCallList(this.listId);
    }

    @Override
    public void finalize() {
        if (this.bufferId >= 0) {
            VboUtil.glDeleteBuffers(this.bufferId);
            this.bufferId = -1;
        }
        if (this.listId >= 0) {
            GL11.glDeleteLists(this.listId, 1);
            this.listId = -1;
        }
    }
}
