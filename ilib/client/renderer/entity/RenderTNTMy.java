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
package ilib.client.renderer.entity;

import ilib.ClientProxy;
import ilib.client.util.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderTNTPrimed;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.item.EntityTNTPrimed;
import org.lwjgl.opengl.GL11;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/21 23:21
 */
public class RenderTNTMy extends RenderTNTPrimed {
    public RenderTNTMy(RenderManager _lvt_1_) {
        super(_lvt_1_);
    }

    @Override
    public void doRender(EntityTNTPrimed entity, double x, double y, double z, float _lvt_8_, float _lvt_9_) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y + 0.5F, (float) z);

        renderFuseCountdown(entity, _lvt_8_, 1.2f);

        GlStateManager.popMatrix();

        super.doRender(entity, x, y, z, _lvt_8_, _lvt_9_);
    }

    private void renderFuseCountdown(EntityTNTPrimed tnt, float partialTicks, float nameOffset) {
        GL11.glTranslatef(0, nameOffset, 0);
        RenderUtils.rotateToPlayer();

        float scale = 0.038F;
        GlStateManager.scale(-scale, -scale, scale);

        int fuse = tnt.getFuse();

        boolean lighting = GL11.glGetBoolean(GL11.GL_LIGHTING);

        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);

        Minecraft mc = ClientProxy.mc;

        String fuseText = ticksToTime(fuse);
        int width = mc.fontRenderer.getStringWidth(fuseText) / 2;

        BufferBuilder buffer = RenderUtils.BUILDER;
        buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos((-width - 1), -1.0D, 0.0D).color(0, 0, 0, 64).endVertex();
        buffer.pos((-width - 1), 8.0D, 0.0D).color(0, 0, 0, 64).endVertex();
        buffer.pos((width + 1), 8.0D, 0.0D).color(0, 0, 0, 64).endVertex();
        buffer.pos((width + 1), -1.0D, 0.0D).color(0, 0, 0, 64).endVertex();
        RenderUtils.TESSELLATOR.draw();

        GlStateManager.enableTexture2D();
        mc.fontRenderer.drawString(fuseText, -width, 0, fuse > 60 ? 553648127 : 0xFFFF0000);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        if (lighting)
            GlStateManager.enableLighting();
        mc.fontRenderer.drawString(fuseText, -width, 0, -1);
        GlStateManager.disableBlend();
        RenderUtils.restoreColor();
    }

    private static String ticksToTime(int ticks) {
        if (ticks > 72000) {
            int h = ticks / 20 / 3600;
            return h + " h";
        }
        if (ticks > 1200) {
            int m = ticks / 20 / 60;
            return m + " m";
        }
        int s = ticks / 20, ms = ticks % 20 / 2;
        return s + "." + ms + " s";
    }
}
