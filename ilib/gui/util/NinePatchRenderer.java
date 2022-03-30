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

package ilib.gui.util;

import ilib.client.RenderUtils;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import roj.opengl.util.Util;
import roj.opengl.util.VboUtil;
import roj.opengl.vertex.VertexBuilder;
import roj.opengl.vertex.VertexFormat;

/**
 * @author Roj234
 * @since 2021/1/13 12:37
 */
public class NinePatchRenderer {
    protected static final VertexFormat P2S_UV2F = VertexFormat.builder().pos(VertexFormat.SHORT, 2).uv2f().build();

    protected int u, v, cell, center;
    protected ResourceLocation texture;

    public NinePatchRenderer(int u, int v, int cell, ResourceLocation texture) {
        this(u, v, cell, cell, texture);
    }

    /**
     * Creates a renderer with given options
     * <p>
     * Texture must be in the following pattern. Cell size is how many pixels each box is
     * <p>
     * *---*---*---*
     * |   |   |   |
     * |   |   |   |
     * *---*---*---*
     * |   |   |   |
     * |   |   |   |
     * *---*---*---*
     * |   |   |   |
     * |   |   |   |
     * *---*---*---*
     * <p>
     * Corners will render one to one
     * Edges will be stretched on their axis
     * Middle will be expanded in both axis (must be solid color)
     */
    public NinePatchRenderer(int u, int v, int cell, int center, ResourceLocation texture) {
        this.u = u;
        this.v = v;
        this.cell = cell;
        this.center = center;
        this.texture = texture;
    }

    public NinePatchRenderer(int cell, ResourceLocation texture) {
        this(cell, cell, texture);
    }

    public NinePatchRenderer(int cell, int center, ResourceLocation texture) {
        this.cell = cell;
        this.center = center;
        this.texture = texture;
    }

    public void setTexture(ResourceLocation loc) {
        this.texture = loc;
    }

    public void setUV(int u, int v) {
        this.u = u;
        this.v = v;
    }

    public int getCellSize() {
        return cell;
    }

    public void setCellSize(int cell) {
        this.cell = cell;
    }

    public int getCenterSize() {
        return center;
    }

    public void setCenterSize(int center) {
        this.center = center;
    }

    public int getTextureSize() {
        return center + (cell << 1);
    }

    protected static void drawNP(VertexBuilder vb, int x, int y, int w, int h, int cell, int u, int v) {
        final float p = 0.00390625F;

        vb.pos(x, y + h).tex(u * p, (v + cell) * p).endVertex();
        vb.pos(x + w, y + h).tex((u + cell) * p, (v + cell) * p).endVertex();
        vb.pos(x + w, y).tex((u + cell) * p, v * p).endVertex();
        vb.pos(x, y).tex(u * p, v * p).endVertex();
    }

    public void render(int x, int y, int w, int h) {
        render(x, y, 0, 0, w, h);
    }

    public void render(int x, int y, int u, int v, int w, int h) {
        if (texture != null) RenderUtils.bindTexture(texture);

        int cs = this.cell;
        int ce = cs + this.center;
        u += this.u;
        v += this.v;

        VertexBuilder vb = Util.sharedVertexBuilder;
        vb.begin(P2S_UV2F);

        // 中间
        drawNP(vb, x + cs, y + cs, w - (cs << 1), h - (cs << 1), cs, u + cs, v + cs);

        // 上方线条
        drawNP(vb, x + cs, y, w - (cs << 1), cs, cs, u + cs, v);
        // 下方线条
        drawNP(vb, x + cs, y + h - cs, w - (cs << 1), cs, cs, u + cs, v + ce);
        // 左方线条
        drawNP(vb, x, y + cs, cs, h - (cs << 1), cs, u, v + cs);
        // 右方线条
        drawNP(vb, x + w - cs, y + cs, cs, h - (cs << 1), cs, u + ce, v + cs);

        // 左上角
        drawNP(vb, x, y, cs, cs, cs, u, v);
        // 右上角
        drawNP(vb, x + w - cs, y, cs, cs, cs, u + ce, v);
        // 左下角
        drawNP(vb, x, y + h - cs, cs, cs, cs, u, v + ce);
        // 右下角
        drawNP(vb, x + w - cs, y + h - cs, cs, cs, cs, u + ce, v + ce);

        // 36 vertices, each: 2 short + 2 float = 12 bytes
        VboUtil.drawVertexes(GL11.GL_QUADS, vb);
    }
}
