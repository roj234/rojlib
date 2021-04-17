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

package ilib.client.renderer;

import ilib.client.util.RenderUtils;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class ArenaRenderer {
    public static final ArenaRenderer INSTANCE = new ArenaRenderer();

    private static final float DTHETA = 0.025F;
    private float r, g, b;

    public ArenaRenderer() {
        r = 1;
    }

    public void setColor(int color) {
        r = ((0xFF & color >> 16) / 255F);
        g = ((0xFF & color >> 8) / 255F);
        b = ((0xFF & color) / 255F);
    }
    
    public void render(BlockPos pos1, BlockPos pos2, float partialTicks, boolean anim) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(pos1.getX(), pos1.getY(), pos1.getZ());

        float lx = pos2.getX() - pos1.getX();
        float ly = pos2.getY() - pos1.getY();
        float lz = pos2.getZ() - pos1.getZ();
        if (lx < 0) {
            GlStateManager.translate(lx, 0, 0);
            lx = -lx;
        }
        if (ly < 0) {
            GlStateManager.translate(0, ly, 0);
            ly = -ly;
        }
        if (lz < 0) {
            GlStateManager.translate(0, 0, lz);
            lz = -lz;
        }
        lx += 1;
        ly += 1;
        lz += 1;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.glLineWidth(5);

        if (anim)
            renderAnimated(lx, ly, lz, partialTicks);
        else
            renderDefault(lx, ly, lz, r, g, b);

        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    public static void renderDefault(float lx, float ly, float lz, float r, float g, float b) {
        BufferBuilder bb = RenderUtils.BUILDER;
        bb.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);

        bb.pos(0, 0, 0).color(r, g, b, 1).endVertex();
        bb.pos(lx, 0, 0).color(r, g, b, 1).endVertex();
        bb.pos(lx, 0, lz).color(r, g, b, 1).endVertex();
        bb.pos(0, 0, lz).color(r, g, b, 1).endVertex();
        bb.pos(0, 0, 0).color(r, g, b, 1).endVertex();

        bb.pos(0, ly, 0).color(r, g, b, 1).endVertex();

        bb.pos(lx, ly, 0).color(r, g, b, 1).endVertex();
        bb.pos(lx, 0, 0).color(r, g, b, 1).endVertex();
        bb.pos(lx, ly, 0).color(r, g, b, 1).endVertex();

        bb.pos(lx, ly, lz).color(r, g, b, 1).endVertex();
        bb.pos(lx, 0, lz).color(r, g, b, 1).endVertex();
        bb.pos(lx, ly, lz).color(r, g, b, 1).endVertex();

        bb.pos(0, ly, lz).color(r, g, b, 1).endVertex();
        bb.pos(0, 0, lz).color(r, g, b, 1).endVertex();
        bb.pos(0, ly, lz).color(r, g, b, 1).endVertex();

        bb.pos(0, ly, 0).color(r, g, b, 1).endVertex();

        RenderUtils.TESSELLATOR.draw();
    }

    public void renderAnimated(float lx, float ly, float lz, float partialTicks) {
        GlStateManager.disableCull();
        
        float theta = DTHETA * partialTicks;

        float r = this.r, g = this.g, b = this.b;

        BufferBuilder bb = RenderUtils.BUILDER;
        bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < lx; i++) {
            for (int j = 0; j < lz; j++) {
                float anime = (MathHelper.sin(theta + j + i) + 1) * 0.5F;
                float lenDis = 0.4F * anime + 0.1F;
                float lenDis_ = 1 - lenDis;
                anime = 1 - anime;

                bb.pos(i + lenDis, -0.025D, j + lenDis).color(r, g, b, anime).endVertex();
                bb.pos(i + lenDis_, -0.025D, j + lenDis).color(r, g, b, anime).endVertex();
                bb.pos(i + lenDis_, -0.025D, j + lenDis_).color(r, g, b, anime).endVertex();
                bb.pos(i + lenDis, -0.025D, j + lenDis_).color(r, g, b, anime).endVertex();

                bb.pos(i + lenDis, ly + 0.025F, j + lenDis).color(r, g, b, anime).endVertex();
                bb.pos(i + lenDis_, ly + 0.025F, j + lenDis).color(r, g, b, anime).endVertex();
                bb.pos(i + lenDis_, ly + 0.025F, j + lenDis_).color(r, g, b, anime).endVertex();
                bb.pos(i + lenDis, ly + 0.025F, j + lenDis_).color(r, g, b, anime).endVertex();
            }
        }

        for (int i = 0; i < lx; i++) {
            for (int j = 0; j < ly; j++) {
                float anime = (MathHelper.sin(theta + j + i) + 1) * 0.5F;
                float lenDis = 0.4F * anime + 0.1F;
                float lenDis_ = 1 - lenDis;
                anime = 1 - anime;
                bb.pos(i + lenDis, j + lenDis, -0.025D).color(r, g, b, anime).endVertex();
                bb.pos(i + lenDis_, j + lenDis, -0.025D).color(r, g, b, anime).endVertex();
                bb.pos(i + lenDis_, j + lenDis_, -0.025D).color(r, g, b, anime).endVertex();
                bb.pos(i + lenDis, j + lenDis_, -0.025D).color(r, g, b, anime).endVertex();

                bb.pos(i + lenDis, j + lenDis, lz + 0.025F).color(r, g, b, anime).endVertex();
                bb.pos(i + lenDis_, j + lenDis, lz + 0.025F).color(r, g, b, anime).endVertex();
                bb.pos(i + lenDis_, j + lenDis_, lz + 0.025F).color(r, g, b, anime).endVertex();
                bb.pos(i + lenDis, j + lenDis_, lz + 0.025F).color(r, g, b, anime).endVertex();
            }
        }

        for (int i = 0; i < ly; i++) {
            for (int j = 0; j < lz; j++) {
                float anime = (MathHelper.sin(theta + j + i) + 1) * 0.5F;
                float lenDis = 0.4F * anime + 0.1F;
                float lenDis_ = 1 - lenDis;
                anime = 1 - anime;
                bb.pos(-0.025D, i + lenDis, j + lenDis).color(r, g, b, anime).endVertex();
                bb.pos(-0.025D, i + lenDis_, j + lenDis).color(r, g, b, anime).endVertex();
                bb.pos(-0.025D, i + lenDis_, j + lenDis_).color(r, g, b, anime).endVertex();
                bb.pos(-0.025D, i + lenDis, j + lenDis_).color(r, g, b, anime).endVertex();

                bb.pos(lx + 0.025F, i + lenDis, j + lenDis).color(r, g, b, anime).endVertex();
                bb.pos(lx + 0.025F, i + lenDis_, j + lenDis).color(r, g, b, anime).endVertex();
                bb.pos(lx + 0.025F, i + lenDis_, j + lenDis_).color(r, g, b, anime).endVertex();
                bb.pos(lx + 0.025F, i + lenDis, j + lenDis_).color(r, g, b, anime).endVertex();
                bb.pos(lx + 0.025F, i + lenDis, j + lenDis).color(r, g, b, anime).endVertex();
            }
        }

        RenderUtils.TESSELLATOR.draw();
        GlStateManager.enableCull();
    }
}
