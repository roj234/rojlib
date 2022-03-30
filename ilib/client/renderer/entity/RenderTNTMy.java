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
import ilib.client.RenderUtils;
import ilib.util.MCTexts;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderTNTPrimed;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.item.EntityTNTPrimed;

/**
 * @author Roj234
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

        GlStateManager.depthMask(false);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);

        FontRenderer fr = ClientProxy.mc.fontRenderer;

        String fuseText = MCTexts.ticksToTime(fuse);
        int width = fr.getStringWidth(fuseText) / 2;

        BufferBuilder buffer = RenderUtils.BUILDER;
        buffer.begin(7, DefaultVertexFormats.POSITION);
        buffer.pos((-width - 1), -1.0D, 0).endVertex();
        buffer.pos((-width - 1), 8.0D, 0).endVertex();
        buffer.pos((width + 1), 8.0D, 0).endVertex();
        buffer.pos((width + 1), -1.0D, 0).endVertex();

        GlStateManager.color(0,0,0, 0.25f);
        RenderUtils.TESSELLATOR.draw();

        int color = fuse > 2000 ? -1 : 0xFFFF0000;

        GlStateManager.enableTexture2D();
        fr.drawString(fuseText, -width, 0, color);
        GlStateManager.depthMask(true);
        fr.drawString(fuseText, -width, 0, color);
        GlStateManager.disableBlend();

        RenderUtils.restoreColor();
    }
}
