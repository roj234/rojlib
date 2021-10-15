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

import org.lwjgl.opengl.GL11;
import roj.asm.nixim.Inject;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import java.awt.*;
import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/11/14 18:05
 */
public abstract class NiximSelectionBox extends RenderGlobal {
    public NiximSelectionBox(Minecraft mcIn) {
        super(mcIn);
    }

    @Override
    @Inject("func_72731_b")
    public void drawSelectionBox(final EntityPlayer player, final RayTraceResult result, final int subID, final float partialTicks) {
        final World world = player.world;
        if (subID == 0 && result.typeOfHit == RayTraceResult.Type.BLOCK) {
            final float prg = getBlockDamage(result);
            /*if (CSB.disableDepthBuffer) {
                GL11.glDisable(2929);
            }*/
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GL11.glLineWidth(1f);
            GlStateManager.disableTexture2D();
            GlStateManager.depthMask(false);
            final BlockPos blockPos = result.getBlockPos();
            final IBlockState blockState = world.getBlockState(blockPos);
            if (blockState.getMaterial() != Material.AIR && world.getWorldBorder().contains(blockPos)) {
                final double d0 = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
                final double d2 = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
                final double d3 = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
                AxisAlignedBB bb = blockState.getSelectedBoundingBox(world, blockPos).offset(-d0, -d2, -d3);
                /*if (CSB.breakAnimation == CSB.DOWN) {
                    bb = bb.expand(0.0, (-breakProgress / 2.0f), 0.0).offset(0.0, (-breakProgress / 2.0f), 0.0);
                }*/
                //else if (CSB.breakAnimation == CSB.SHRINK) {
                    bb = bb.expand((-prg / 2.0f), (-prg / 2.0f), (-prg / 2.0f));
                //}
                float red = 1;
                float green = 1;
                float blue = 1;
                float blinkAlpha = /*(CSB.breakAnimation == CSB.ALPHA) ? */prg;
                float outlineAlpha = 1;
                //if (CSB.rainbow) {
                    final float millis = System.currentTimeMillis() % 10000L / 10000.0f;
                    final int color = Color.HSBtoRGB(millis, 0.8f, 0.8f);
                    red = (color >>> 16 & 0xFF) / 255.0f;
                    green = (color >>> 8 & 0xFF) / 255.0f;
                    blue = (color & 0xFF) / 255.0f;
                //}
                drawBlinkingBlock(bb.expand(0.002, 0.002, 0.002), red, green, blue, blinkAlpha);
                drawOutlinedBoundingBox(bb.expand(0.002, 0.002, 0.002), red, green, blue, outlineAlpha);
            }
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            /*if (CSB.disableDepthBuffer) {
                GL11.glEnable(2929);
            }*/
        }
    }

    private static float getBlockDamage(final RayTraceResult rtr) {
        final Map<Integer, DestroyBlockProgress> map = Minecraft.getMinecraft().renderGlobal.damagedBlocks;
        for (final Map.Entry<Integer, DestroyBlockProgress> entry : map.entrySet()) {
            final DestroyBlockProgress progress = entry.getValue();
            if (progress.getPosition().equals(rtr.getBlockPos()) && progress.getPartialBlockDamage() >= 0 && progress.getPartialBlockDamage() <= 10) {
                return progress.getPartialBlockDamage() / 10f;
            }
        }
        return 0;
    }

    private static void drawOutlinedBoundingBox(final AxisAlignedBB boundingBox, final float red, final float green, final float blue, final float alpha) {
        final Tessellator tessellator = Tessellator.getInstance();
        final BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(3, DefaultVertexFormats.POSITION_COLOR);
        final double minX = boundingBox.minX;
        final double minY = boundingBox.minY;
        final double minZ = boundingBox.minZ;
        final double maxX = boundingBox.maxX;
        final double maxY = boundingBox.maxY;
        final double maxZ = boundingBox.maxZ;
        buffer.pos(minX, minY, minZ).color(red, green, blue, 0.0f).endVertex();
        buffer.pos(minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(minX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(minX, maxY, maxZ).color(red, green, blue, 0.0f).endVertex();
        buffer.pos(minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, maxY, maxZ).color(red, green, blue, 0.0f).endVertex();
        buffer.pos(maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, maxY, minZ).color(red, green, blue, 0.0f).endVertex();
        buffer.pos(maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(maxX, minY, minZ).color(red, green, blue, 0.0f).endVertex();
        tessellator.draw();
    }

    private static void drawBlinkingBlock(final AxisAlignedBB alignedBB, final float red, final float green, final float blue, float alpha) {
        final Tessellator tessellator = Tessellator.getInstance();
        if (alpha > 0.0f) {
            float blinkSpeed = 0.5f;
            if (blinkSpeed > 0.0f) {
                alpha *= (float)Math.abs(Math.sin(Minecraft.getSystemTime() / 100.0 * blinkSpeed));
            }
            final BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(5, DefaultVertexFormats.POSITION_COLOR);
            RenderGlobal.addChainedFilledBoxVertices(buffer, alignedBB.minX, alignedBB.minY, alignedBB.minZ, alignedBB.maxX, alignedBB.maxY, alignedBB.maxZ, red, green, blue, alpha);
            tessellator.draw();
        }
    }
}
