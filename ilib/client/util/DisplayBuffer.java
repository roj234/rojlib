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

import ilib.ATHandler;
import ilib.ImpLib;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import java.util.List;
import java.util.function.Consumer;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class DisplayBuffer {
    private int bufferId = -1;
    private int vertexCount;
    private final VertexFormat vertexFormat;
    private final Consumer<BufferBuilder> drawable;

    private int mode;

    public static final boolean vboSupported = OpenGlHelper.vboSupported;

    public DisplayBuffer(VertexFormat format, Consumer<BufferBuilder> drawable) {
        this.vertexFormat = format;
        this.drawable = drawable;
    }

    @SuppressWarnings("fallthrough")
    public static void preDrawVBO(VertexFormatElement attr, VertexFormat format, int index) {
        int count = attr.getElementCount();
        int constant = attr.getType().getGlConstant();
        switch (attr.getUsage()) {
            case POSITION:
                GlStateManager.glVertexPointer(count, constant, index, format.getOffset(index));
                GlStateManager.glEnableClientState(32884);
                break;
            case COLOR:
                GlStateManager.glColorPointer(count, constant, index, format.getOffset(index));
                GlStateManager.glEnableClientState(32886);
                break;
            case UV:
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit + attr.getIndex());
                GlStateManager.glTexCoordPointer(count, constant, index, format.getOffset(index));
                GlStateManager.glEnableClientState(32888);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
            case PADDING:
                break;
            case GENERIC:
                GL20.glEnableVertexAttribArray(attr.getIndex());
                GL20.glVertexAttribPointer(attr.getIndex(), count, constant, false, index, format.getOffset(index));
                break;
            default:
                ImpLib.logger().fatal("Unimplemented vanilla attribute upload: {}", attr.getUsage().getDisplayName());
        }

    }

    @SuppressWarnings("fallthrough")
    public static void postDrawVBO(VertexFormatElement attr) {
        switch (attr.getUsage()) {
            case POSITION:
                GlStateManager.glDisableClientState(32884);
                break;
            case COLOR:
                GlStateManager.glDisableClientState(32886);
                GlStateManager.resetColor();
                break;
            case UV:
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit + attr.getIndex());
                GlStateManager.glDisableClientState(32888);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
            case PADDING:
                break;
            case GENERIC:
                GL20.glDisableVertexAttribArray(attr.getIndex());
                break;
            default:
                ImpLib.logger().fatal("Unimplemented vanilla attribute upload: {}", attr.getUsage().getDisplayName());
        }
    }

    public void firstDraw(int mode) {
        this.mode = mode;
        if (vboSupported) {
            if (this.bufferId < 0) {
                this.bufferId = OpenGlHelper.glGenBuffers();
            }
            OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, this.bufferId);
        } else {
            if (this.bufferId < 0) {
                this.bufferId = GLAllocation.generateDisplayLists(1);
            }
            GlStateManager.glNewList(this.bufferId, GL11.GL_COMPILE);
        }
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(mode, vertexFormat);

        drawable.accept(builder);

        if (vboSupported) {
            if (ATHandler.isDrawing(builder)) {
                builder.finishDrawing();
            }

            OpenGlHelper.glBufferData(OpenGlHelper.GL_ARRAY_BUFFER, builder.getByteBuffer(), GL15.GL_STATIC_DRAW);
            OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);
            this.vertexCount = builder.getVertexCount();

            builder.reset();
        } else {
            if (ATHandler.isDrawing(builder)) {
                Tessellator.getInstance().draw();
            }
            GlStateManager.glEndList();
        }
    }

    public void draw() {
        if (vboSupported) {
            OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, this.bufferId);
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);

            List<VertexFormatElement> list = vertexFormat.getElements();

            for (int i = 0, j = list.size(); i < j; i++) {
                VertexFormatElement element = list.get(i);
                preDrawVBO(element, vertexFormat, i);
            }

            GlStateManager.glDrawArrays(mode, 0, this.vertexCount);
            OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);

            for (int i = 0, j = list.size(); i < j; i++) {
                postDrawVBO(list.get(i));
            }
        } else {
            GL11.glCallList(this.bufferId);
        }
    }

    @Override
    protected void finalize() {
        deleteGlBuffers();
    }

    public void deleteGlBuffers() {
        if (this.bufferId >= 0) {
            if (vboSupported)
                OpenGlHelper.glDeleteBuffers(this.bufferId);
            else
                GLAllocation.deleteDisplayLists(this.bufferId);
            this.bufferId = -1;
        }
    }
}
