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
/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: RenderUtils.java
 */
package ilib.client.util;

import ilib.ClientProxy;
import ilib.util.ForgeUtil;
import ilib.util.TimeUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fml.common.LoaderState;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import roj.math.Mat4f;
import roj.math.Mat4x3f;
import roj.math.Vec3f;
import roj.util.Color;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;


public class RenderUtils {
    public static final ResourceLocation MC_BLOCKS_RESOURCE_LOCATION = TextureMap.LOCATION_BLOCKS_TEXTURE;
    public static final ResourceLocation MC_ITEMS_RESOURCE_LOCATION = new ResourceLocation("textures/atlas/items.png");

    public static final TextureManager TEXTURE_MANAGER;
    public static final RenderManager RENDER_MANAGER;
    public static final BlockRendererDispatcher BLOCK_RENDERER;
    public static final Tessellator TESSELLATOR;
    public static final BufferBuilder BUILDER;
    public static final TextureMap TEXMAP_BLOCK;
    public static final ItemRenderer ITEM_RENDERER;

    private static ByteBuffer buffer;

    static {
        if (!ForgeUtil.hasReachedState(LoaderState.INITIALIZATION))
            throw new IllegalStateException("Too early!");

        Minecraft mc = ClientProxy.mc;
        TEXTURE_MANAGER = mc.getTextureManager();
        RENDER_MANAGER = mc.getRenderManager();
        BLOCK_RENDERER = mc.getBlockRendererDispatcher();
        TEXMAP_BLOCK = mc.getTextureMapBlocks();
        TESSELLATOR = Tessellator.getInstance();
        BUILDER = TESSELLATOR.getBuffer();
        ITEM_RENDERER = mc.getItemRenderer();
        buffer = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
    }

    public static ByteBuffer getAndResetBuffer() {
        buffer.clear();
        return buffer;
    }

    /**
     * 扩容
     *
     * @param capacity The capacity required
     */
    public static void ensureCapacity(final int capacity) {
        if (buffer.capacity() < capacity) {
            int cap = buffer.capacity();
            while (cap < capacity) {
                if (cap > 262144) {
                    throw new OutOfMemoryError("Too large buffer size " + cap + "(need: " + capacity + ")");
                }
                cap <<= 1;
            }
            ByteBuffer buf = ByteBuffer.allocateDirect(cap).order(ByteOrder.nativeOrder());
            buf.put(buffer);
            buffer = buf;
        }
    }

    /**
     * Draw a part of a 256 x 256 pixels image file at the current position
     *
     * <p>
     * The first two arguments are the {@param x x},{@param y y} position that you want to draw the texture at
     * (with respect to the current transformations).
     * </p>
     * <p>
     * The next four arguments specify what part of the image file to draw, in the order below:
     * </p>
     *
     * @param u Left-most side
     * @param v Top-most side
     * @param w Right-most side
     * @param h Bottom-most side
     *          <p>
     *          The units of these four arguments are pixels in the image file. These arguments will form a
     *          rectangle, which is then "cut" from your image and used as the texture
     *          </p>
     * @since 2020-04-24 12:00:00
     */
    public static void fastRect(int x, int y, float u, float v, int w, int h) {
        final float pw = 0.00390625F;
        fastRect0(x, y, u, v, w, h, pw, pw);
    }

    /**
     * Draw a part of a {@param imgW width} x {@param imgH height} pixels image file at the current position
     * <p>
     * The first two arguments are the {@param x x},{@param y y} position that you want to draw the texture at
     * (with respect to the current transformations).
     * </p>
     * <p>
     * The next four arguments specify what part of the image file to draw, in the order below:
     * </p>
     *
     * @param u Left-most side
     * @param v Top-most side
     * @param w Right-most side
     * @param h Bottom-most side
     *
     *          <p>
     *          The units of these four arguments are pixels in the image file. These arguments will form a
     *          rectangle, which is then "cut" from your image and used as the texture
     *          </p>
     * @since 2020-04-24 12:00:00
     */
    public static void fastRect(int x, int y, float u, float v, int w, int h, float imgW, float imgH) {
        float pw = 1.0F / imgW;
        float ph = 1.0F / imgH;
        fastRect0(x, y, u, v, w, h, pw, ph);
    }

    public static void fastScaledRect(int x, int y, float u, float v, int w, int h, int showW, int showH, float pw, float ph) {
        ensureCapacity(64);
        getAndResetBuffer();
        VFR(x, y + showH, u * pw, (v + h) * ph);
        VFR(x + showW, y + showH, (u + w) * pw, (v + h) * ph);
        VFR(x + showW, y, (u + w) * pw, v * ph);
        VFR(x, y, u * pw, v * ph);
        drawPosTexRect(buffer, 4);
    }

    public static void fastRect0(int x, int y, float u, float v, int w, int h, float pw, float ph) {
        ensureCapacity(64);
        getAndResetBuffer();
        VFR(x, y + h, u * pw, (v + h) * ph);
        VFR(x + w, y + h, (u + w) * pw, (v + h) * ph);
        VFR(x + w, y, (u + w) * pw, v * ph);
        VFR(x, y, u * pw, v * ph);
        drawPosTexRect(buffer, 4);
    }

    public static void VFR(int x, int y, float u, float v) {
        buffer.putInt(x).putInt(y).putFloat(u).putFloat(v);
    }

    /**
     * 画一些带有材质的矩形
     *
     * @param buffer The buffer
     * @param count  vertex数量
     */
    public static void drawPosTexRect(ByteBuffer buffer, int count) {
        GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);

        // glPos2i
        buffer.position(0);
        GlStateManager.glVertexPointer(2, GL11.GL_INT, 16, buffer);
        // glTex2f
        buffer.position(8);
        GlStateManager.glTexCoordPointer(2, GL11.GL_FLOAT, 16, buffer);

        GlStateManager.glDrawArrays(GL11.GL_QUADS, 0, count);

        GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
    }

    public static void bindTexture(ResourceLocation resource) {
        TEXTURE_MANAGER.bindTexture(resource);
    }

    public static void bindMinecraftItemSheet() {
        bindTexture(MC_ITEMS_RESOURCE_LOCATION);
    }

    public static void bindMinecraftBlockSheet() {
        bindTexture(MC_BLOCKS_RESOURCE_LOCATION);
    }

    public static void setColor(int color) {
        setColor(color, 1);
    }

    public static void setColor(int color, float alpha) {
        float red = (color >> 16 & 0xFF) / 255F;
        float green = (color >> 8 & 0xFF) / 255F;
        float blue = (color & 0xFF) / 255F;
        GlStateManager.color(red, green, blue, alpha);
    }

    public static void setColor(Color color) {
        GlStateManager.color(color.getRedP(), color.getGreenP(), color.getBlueP(), color.getAlphaP());
    }

    public static void colorWhite() {
        GlStateManager.color(1, 1, 1, 1);
    }

    public static void restoreColor() {
        GlStateManager.color(0, 0, 0, 0);
    }

    public static void rotateToPlayer() {
        GlStateManager.rotate(-RENDER_MANAGER.playerViewY, 0, 1, 0);
        GlStateManager.rotate(RENDER_MANAGER.playerViewX, 1, 0, 0);
    }

    public static void prepareRenderState() {
        colorWhite();
        GlStateManager.disableRescaleNormal();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
    }

    public static void restoreRenderState() {
        colorWhite();
        GlStateManager.enableRescaleNormal();
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableLighting();
        //GlStateManager.disableDepth();
        //GL11.glDisable(GL11.GL_DEPTH_TEST); 隐藏面剔除
        GlStateManager.disableAlpha();
    }

    // region Quad(Square)

    public static void renderQuadBright(double scale, int brightness) {
        renderQuadBrightAlpha(scale, brightness, 128);
    }

    public static void renderQuadBrightAlpha(double scale, int brightness, int alpha) {
        renderQuadBrightAlpha(scale, scale, brightness, alpha);
    }

    public static void renderQuadBrightAlpha(double x, double y, int brightness, int alpha) {
        int b1 = brightness >> 16 & '\uffff';
        int b2 = brightness & '\uffff';
        BUILDER.begin(7, DefaultVertexFormats.POSITION_TEX_LMAP_COLOR);
        renderQuadBrightAlphaIn(x, y, b1, b2, alpha);
        TESSELLATOR.draw();
        GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
        BUILDER.begin(7, DefaultVertexFormats.POSITION_TEX_LMAP_COLOR);
        renderQuadBrightAlphaIn(x, y, b1, b2, alpha);
        TESSELLATOR.draw();
    }

    public static void renderQuadBrightAlphaIn(double x, double y, int b1, int b2, int alpha) {
        Quad0(x, y, b1, b2, alpha);
        Quad0(-x, y, b1, b2, alpha);
        Quad0(-x, -y, b1, b2, alpha);
        Quad0(x, -y, b1, b2, alpha);
    }

    private static void Quad0(double x, double y, int b1, int b2, int alpha) {
        BUILDER.pos(x, -y, 0.0D).tex(1.0D, 0.0D).lightmap(b1, b2).color(255, 255, 255, alpha).endVertex();
    }

    // endregion

    private static void Rect0(double x, double y, double z, int color) {
        BUILDER.pos(x, y, z).color(color >>> 16 & 0xff, color >>> 8 & 0xff, color & 0xff, color >> 24 & 0xff).endVertex();
    }

    public static void drawRectangle(double x, double y, int zLevel, double width, double height, int color, float alpha) {
        BUILDER.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        int rgba = color | ((int) (alpha * 255.0f)) << 24;

        Rect0(x, height + y, zLevel, rgba);
        Rect0(x + width, height + y, zLevel, rgba);
        Rect0(x + width, y, zLevel, rgba);
        Rect0(x, y, zLevel, rgba);
        TESSELLATOR.draw();
    }

    // region Block

    public static void renderBlock(World world, BlockPos pos) {
        renderBlock(world, pos, world.getBlockState(pos));
    }

    public static void renderBlock(World world, BlockPos pos, IBlockState state) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(-0.5, -0.5, -0.5);
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        //GL11.glEnable(GL11.GL_DEPTH_TEST);

        BUILDER.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);

        IBakedModel model = BLOCK_RENDERER.getModelForState(state);
        final boolean NOT_RENDER_HIDDEN = false;
        BLOCK_RENDERER.getBlockModelRenderer().renderModelFlat(world, model, state, pos, BUILDER, NOT_RENDER_HIDDEN, TimeUtil.tick);

        TESSELLATOR.draw();
        //GL11.glDisable(GL11.GL_DEPTH_TEST);
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
    }

    // endregion
    // region Fluid

    public static void drawIconWithCut(TextureAtlasSprite icon, int x, int y, int w, int h, int cut) {
        float minU = icon.getMinU(),
                u = minU + (icon.getMaxU() - minU) * w / 16,
                minV = icon.getMinV(),
                v = minV + (icon.getMaxV() - minV) * h / 16;
        ensureCapacity(64);
        getAndResetBuffer();
        VFR(x, y + h, minU, v);
        VFR(x + w, y + h, u, v);
        VFR(x + w, y + cut, u, minV);
        VFR(x, y + cut, minU, minV);
        drawPosTexRect(buffer, 4);
    }

    /**
     * Renders a fluid from the given tank
     *
     * @param tank      The tank
     * @param x         The x pos
     * @param y         The y pos
     * @param maxHeight Max height
     * @param maxWidth  Max width
     */
    public static void renderFluid(FluidTank tank, int x, int y, int maxHeight, int maxWidth) {
        FluidStack fluid = tank.getFluid();
        if (fluid != null) {
            GlStateManager.pushMatrix();
            int level = (fluid.amount * maxHeight) / tank.getCapacity();
            TextureAtlasSprite icon = TEXMAP_BLOCK.getAtlasSprite(fluid.getFluid().getStill(fluid).toString());
            bindMinecraftBlockSheet();
            setColor(fluid.getFluid().getColor(fluid));

            int timesW = maxWidth / 16;
            int cutW = 16;

            for (int j = 0; j <= timesW; j++) {
                if (j == timesW)
                    cutW = maxWidth % 16;
                if (level >= 16) {
                    double times = Math.floor(level / 16f);
                    for (int i = 1; i <= times; i++) {
                        drawIconWithCut(icon, x + j * 16, y - 16 * i, cutW, 16, 0);
                    }
                    int cut = level % 16;
                    drawIconWithCut(icon, x + j * 16, (int) (y - 16.0D * (times + 1.0D)), cutW, 16, 16 - cut);
                } else {
                    int cut = level % 16;
                    drawIconWithCut(icon, x + j * 16, y - 16, cutW, 16, 16 - cut);
                }
            }
            GlStateManager.popMatrix();
        }
    }

    // endregion
    // region Trackball

    static ByteBuffer  mb0          = BufferUtils.createByteBuffer(16 * 4);
    static FloatBuffer matrixBuffer = mb0.asFloatBuffer();

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

    public static void loadMatrix(Mat4x3f transform) {
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
                .put(0)
                .put(0)
                .put(0)
                .put(1)
                .flip();
        GL11.glMultMatrix(matrixBuffer);
    }

    public static Mat4f createEntityRotateMatrix(Entity entity) {
        double yaw = Math.toRadians(entity.rotationYaw - 180);
        double pitch = Math.toRadians(entity.rotationPitch);

        return new Mat4f().rotate(new Vec3f(1, 0, 0), (float) pitch)
                .rotate(new Vec3f(0, 1, 0), (float) yaw);
    }

    // endregion

    public static void drawCircle(double cx, double cy, double r) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        double angle = 0;
        double delta = Math.PI / 36.0;

        GL11.glVertex2d(cx, cy);
        while(angle < Math.PI * 2) {
            double x = cx + (r * Math.cos(angle));
            double y = cy + (r * Math.sin(angle));

            GL11.glVertex2d(x, y);

            angle += delta;
        }
        GL11.glEnd();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /**
     * 圆柱
     */
    public static void drawCylinder(float cx, float cy, float cz, float r, float height, float teXs, float teYs, float teXe, float teYe){
        float x, z;
        float x2, z2;

        double angle = 0;
        double delta = Math.PI / 20.0;

        GL11.glBegin(GL11.GL_QUADS);
        while(angle < Math.PI * 2) {
            x = (float) (cx + (Math.sin(angle) * r));
            z = (float) (cz + (Math.cos(angle) * r));

            x2 = (float) (cx + (Math.sin(angle + delta) * r));
            z2 = (float) (cz + (Math.cos(angle + delta) * r));

            GL11.glTexCoord2f(teXs, teYs);
            GL11.glVertex3f(x2, cy, z2);

            GL11.glTexCoord2f(teXe, teYs);
            GL11.glVertex3f(x2, cy + height, z2);

            GL11.glTexCoord2f(teXe, teYe);
            GL11.glVertex3f(x, cy + height, z);

            GL11.glTexCoord2f(teXs, teYe);
            GL11.glVertex3f(x, cy, z);

            angle += delta;
        }
        GL11.glEnd();
    }
}
