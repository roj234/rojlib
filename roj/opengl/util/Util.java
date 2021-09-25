/*
 * This file is a part of MoreItems
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
package roj.opengl.util;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import roj.math.Mat4f;
import roj.opengl.vertex.VertexBuilder;
import roj.opengl.vertex.VertexFormat;
import roj.opengl.vertex.VertexFormats;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Rendering Util
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/9/18 15:42
 */
public class Util {
    public static VertexBuilder sharedVertexBuilder;

    public static void color(float red, float green, float blue) {
        GL11.glColor4f(red, green, blue, 1);
    }

    public static void color(int color, float alpha) {
        float red = (color >> 16 & 0xFF) / 255F;
        float green = (color >> 8 & 0xFF) / 255F;
        float blue = (color & 0xFF) / 255F;
        GL11.glColor4f(red, green, blue, alpha);
    }

    /**
     * 圆柱
     */
    public static void drawCylinder(float cx, float cy, float cz, float r, float height) {
        VertexBuilder bb = sharedVertexBuilder;
        float x, z;
        float x2, z2;

        double angle = 0;
        double delta = Math.PI / 16.0;

        while(angle < Math.PI * 2) {
            x = (float) (cx + (Math.sin(angle) * r));
            z = (float) (cz + (Math.cos(angle) * r));

            x2 = (float) (cx + (Math.sin(angle + delta) * r));
            z2 = (float) (cz + (Math.cos(angle + delta) * r));

            bb.pos(x2, cy, z2).endVertex();
            bb.pos(x2, cy + height, z2).endVertex();
            bb.pos(x, cy + height, z).endVertex();
            bb.pos(x, cy, z).endVertex();

            angle += delta;
        }
    }

    public static void drawCircleZ(double cx, double cy, double cz, double r) {
        VertexBuilder bb = sharedVertexBuilder;
        double angle = 0;
        double delta = Math.PI / 24.0;

        bb.pos(cx, cz, cy).endVertex();
        while(angle < Math.PI * 2) {
            double x = cx + (r * Math.cos(angle));
            double y = cy + (r * Math.sin(angle));

            bb.pos(x, cz, y).endVertex();

            angle += delta;
        }
    }

    static final VertexFormat POS2 = VertexFormat.builder().pos(VertexFormat.FLOAT, 2).build();
    public static void drawCircle(double cx, double cy, double r) {
        VertexBuilder bb = sharedVertexBuilder;
        double angle = 0;
        double delta = Math.PI / 24.0;

        bb.begin(POS2);
        bb.pos(cx, cy, 0).endVertex();
        while(angle < Math.PI * 2) {
            double x = cx + (r * Math.cos(angle));
            double y = cy + (r * Math.sin(angle));

            bb.pos(x, y, 0).endVertex();

            angle += delta;
        }
        VboUtil.drawVertexes(GL11.GL_TRIANGLE_FAN, bb);
    }

    public static void drawXYZ(int length) {
        GL11.glLineWidth(2);
        VertexBuilder bb = sharedVertexBuilder;
        // Axis
        bb.begin(VertexFormats.POSITION_COLOR);
        // X
        bb.pos(.5f, .5f, .5f).color(255, 0, 0, 255).endVertex();
        bb.pos(length, .5f, .5f).color(255, 0, 0, 255).endVertex();
        // Y
        bb.pos(.5f, .5f, .5f).color(0, 255, 0, 255).endVertex();
        bb.pos(.5f, length, .5f).color(0, 255, 0, 255).endVertex();
        // Z
        bb.pos(.5f, .5f, length).color(0, 0, 255, 255).endVertex();
        bb.pos(.5f, .5f, .5f).color(0, 0, 255, 255).endVertex();
        bb.end();
        VboUtil.drawVertexes(GL11.GL_LINES, bb);
        // End Axis
    }

    public static void bindTexture(int id) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
    }

    static final ByteBuffer  mb0          = BufferUtils.createByteBuffer(16 * 4);
    static final FloatBuffer matrixBuffer = mb0.asFloatBuffer();

    public static void loadMatrix(Mat4f transform) {
        matrixBuffer.put(transform.m00)
                    .put(transform.m01)
                    .put(transform.m02)
                    .put(transform.m03)
                    .put(transform.m10)
                    .put(transform.m11)
                    .put(transform.m12)
                    .put(transform.m13)
                    .put(transform.m20)
                    .put(transform.m21)
                    .put(transform.m22)
                    .put(transform.m23)
                    .put(transform.m30)
                    .put(transform.m31)
                    .put(transform.m32)
                    .put(transform.m33)
                    .flip();
        GL11.glMultMatrix(matrixBuffer);
    }
}
