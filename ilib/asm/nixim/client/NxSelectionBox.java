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
package ilib.asm.nixim.client;

import ilib.client.RenderUtils;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import org.lwjgl.opengl.GL11;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

/**
 * @author Roj234
 * @since  2020/11/14 18:05
 */
@Nixim("net.minecraft.client.renderer.RenderGlobal")
abstract class NxSelectionBox extends RenderGlobal {
    public NxSelectionBox(Minecraft mcIn) {
        super(mcIn);
    }

    @Override
    @Inject("func_72731_b")
    public void drawSelectionBox(EntityPlayer p, RayTraceResult result, int subID, float partialTicks) {
        if (subID == 0 && result.typeOfHit == RayTraceResult.Type.BLOCK) {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.glLineWidth(2);
            GlStateManager.disableTexture2D();
            GlStateManager.depthMask(false);

            BlockPos pos = result.getBlockPos();
            IBlockState state = world.getBlockState(pos);
            if (state.getMaterial() != Material.AIR && world.getWorldBorder().contains(pos)) {
                double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * partialTicks;
                double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * partialTicks;
                double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * partialTicks;
                AxisAlignedBB bb = state.getSelectedBoundingBox(world, pos).offset(-x, -y, -z).grow(0.002);

                drawBlinkBlock(bb, 1, 1, 1, 0.4f);
                drawSelectionBoundingBox(bb, 1, 1, 1, 0.8F);
            }

            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
        }
    }

    @Copy
    private static void drawBlinkBlock(AxisAlignedBB box, float r, float g, float b, float a) {
        a *= Math.abs(1 - (Minecraft.getSystemTime() / 400.0f) % 2);

        if (a > 1e-3) {
            BufferBuilder bb = RenderUtils.BUILDER;
            bb.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            addChainedFilledBoxVertices(bb, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, a);
            RenderUtils.TESSELLATOR.draw();
        }
    }
}
