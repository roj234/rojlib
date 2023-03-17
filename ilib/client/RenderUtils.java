/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: RenderUtils.java
 */
package ilib.client;

import ilib.ClientProxy;
import ilib.client.misc.LightAccess;
import ilib.util.ForgeUtil;
import ilib.util.TimeUtil;
import org.lwjgl.opengl.GL11;
import roj.math.Mat4x3f;
import roj.opengl.util.Util;

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
import net.minecraft.world.IBlockAccess;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fml.common.LoaderState;

import java.awt.*;


public class RenderUtils {
	public static final TextureManager TEXTURE_MANAGER;
	public static final RenderManager RENDER_MANAGER;
	public static final BlockRendererDispatcher BLOCK_RENDERER;
	public static final Tessellator TESSELLATOR;
	public static final BufferBuilder BUILDER;
	public static final TextureMap TEXMAP_BLOCK;
	public static final ItemRenderer ITEM_RENDERER;

	static {
		if (!ForgeUtil.hasReachedState(LoaderState.INITIALIZATION)) throw new IllegalStateException("!hasReachedState(INITIALIZATION)");

		Minecraft mc = ClientProxy.mc;
		TEXTURE_MANAGER = mc.getTextureManager();
		RENDER_MANAGER = mc.getRenderManager();
		BLOCK_RENDERER = mc.getBlockRendererDispatcher();
		TEXMAP_BLOCK = mc.getTextureMapBlocks();
		TESSELLATOR = Tessellator.getInstance();
		BUILDER = TESSELLATOR.getBuffer();
		ITEM_RENDERER = mc.getItemRenderer();
	}

	public static void fastRect(int x, int y, int u, int v, int w, int h) {
		final float pw = 0.00390625F;
		fastRect0(x, y, u, v, w, h, pw, pw);
	}

	public static void fastRect(int x, int y, int u, int v, int w, int h, float imgW, float imgH) {
		float pw = 1.0F / imgW;
		float ph = 1.0F / imgH;
		fastRect0(x, y, u, v, w, h, pw, ph);
	}

	public static void fastScaledRect(int x, int y, int u, int v, int w, int h, int showW, int showH, float pw, float ph) {
		BUILDER.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

		VFR(x, y + showH, u * pw, (v + h) * ph);
		VFR(x + showW, y + showH, (u + w) * pw, (v + h) * ph);
		VFR(x + showW, y, (u + w) * pw, v * ph);
		VFR(x, y, u * pw, v * ph);

		TESSELLATOR.draw();
	}

	private static void fastRect0(int x, int y, int u, int v, int w, int h, float pw, float ph) {
		BUILDER.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

		VFR(x, y + h, u * pw, (v + h) * ph);
		VFR(x + w, y + h, (u + w) * pw, (v + h) * ph);
		VFR(x + w, y, (u + w) * pw, v * ph);
		VFR(x, y, u * pw, v * ph);

		TESSELLATOR.draw();
	}

	private static void VFR(int x, int y, float u, float v) {
		BUILDER.pos(x, y, 0).tex(u, v).endVertex();
	}

	public static void bindTexture(ResourceLocation resource) {
		TEXTURE_MANAGER.bindTexture(resource);
	}

	public static void bindMinecraftBlockSheet() {
		bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
	}

	public static void setColor(int color) {
		float red = (color >> 16 & 0xFF) / 255F;
		float green = (color >> 8 & 0xFF) / 255F;
		float blue = (color & 0xFF) / 255F;
		GlStateManager.color(red, green, blue, 1);
	}

	public static void setColor(Color color) {
		int rgb = color.getRGB();
		GlStateManager.color(((rgb >>> 16) & 0xFF) / 255f, ((rgb >>> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, ((rgb >>> 24) & 0xFF) / 255f);
	}

	public static void colorWhite() {
		GlStateManager.color(1, 1, 1, 1);
	}

	public static void rotateToPlayer() {
		GlStateManager.rotate(-RENDER_MANAGER.playerViewY, 0, 1, 0);
		GlStateManager.rotate(RENDER_MANAGER.playerViewX, 1, 0, 0);
	}

	public static void prepareRenderState() {
		colorWhite();
		// 重置法线
		GlStateManager.disableRescaleNormal();
		// 在激活光照的情况下用glColor函数给物体上色（颜色追踪）
		GlStateManager.disableColorMaterial();
		GlStateManager.disableLighting();
	}

	public static void restoreRenderState() {
		colorWhite();
	}

	// region Quad(Square)


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

	public static void drawRectangle(double x, double y, int zLevel, double width, double height, int argb) {
		BUILDER.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

		Rect0(x, height + y, zLevel, argb);
		Rect0(x + width, height + y, zLevel, argb);
		Rect0(x + width, y, zLevel, argb);
		Rect0(x, y, zLevel, argb);
		TESSELLATOR.draw();
	}

	// region Block

	public static void renderBlock(IBlockAccess world, BlockPos pos) {
		renderBlock(world, pos, world.getBlockState(pos));
	}

	public static void renderBlock(IBlockAccess world, BlockPos pos, IBlockState state) {
		GlStateManager.pushMatrix();
		GlStateManager.translate(-0.5, -0.5, -0.5);

		BUILDER.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);

		IBakedModel model = BLOCK_RENDERER.getModelForState(state);
		if (world == null) world = new LightAccess(state);
		BLOCK_RENDERER.getBlockModelRenderer().renderModelFlat(world, model, state, pos, BUILDER, false, TimeUtil.tick);

		TESSELLATOR.draw();
		GlStateManager.popMatrix();
	}

	// endregion
	// region Fluid

	public static void drawIconWithCut(TextureAtlasSprite icon, int x, int y, int w, int h, int cut) {
		float minU = icon.getMinU(), u = minU + (icon.getMaxU() - minU) * w / 16, minV = icon.getMinV(), v = minV + (icon.getMaxV() - minV) * h / 16;

		BUILDER.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

		VFR(x, y + h, minU, v);
		VFR(x + w, y + h, u, v);
		VFR(x + w, y + cut, u, minV);
		VFR(x, y + cut, minU, minV);

		TESSELLATOR.draw();
	}

	/**
	 * Renders a fluid from the given tank
	 */
	public static void renderFluid(FluidTank tank, int x, int y, int maxWidth, int maxHeight) {
		FluidStack fluid = tank.getFluid();
		if (fluid != null) {
			int level = (fluid.amount * maxHeight) / tank.getCapacity();
			TextureAtlasSprite icon = TEXMAP_BLOCK.getAtlasSprite(fluid.getFluid().getStill(fluid).toString());
			bindMinecraftBlockSheet();
			setColor(fluid.getFluid().getColor(fluid));

			int timesW = maxWidth / 16;
			int cutW = 16;

			for (int j = 0; j <= timesW; j++) {
				if (j == timesW) cutW = maxWidth % 16;
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
		}
	}

	// endregion
	// region Trackball

	public static void loadMatrix(Mat4x3f m) {
		Util.loadMatrix(m);
	}

	public static Mat4x3f createEntityRotateMatrix(Entity entity) {
		float yaw = (float) Math.toRadians(entity.rotationYaw);
		float pitch = (float) Math.toRadians(entity.rotationPitch);

		return new Mat4x3f().rotateY((float) Math.PI - yaw).rotateX(-pitch);
	}

	// endregion

	public static void drawCircle(double cx, double cy, double r) {
		Util.drawCircle(cx, cy, r);
	}

	/**
	 * 圆柱
	 */
	public static void drawCylinder(float cx, float cy, float cz, float r, float height, float u0, float v0, float u1, float v1) {
		float x, z;
		float x2, z2;

		double angle = 0;
		double delta = Math.PI / 20.0;

		BUILDER.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
		while (angle < Math.PI * 2) {
			x = (float) (cx + (Math.sin(angle) * r));
			z = (float) (cz + (Math.cos(angle) * r));

			angle += delta;

			x2 = (float) (cx + (Math.sin(angle) * r));
			z2 = (float) (cz + (Math.cos(angle) * r));

			BUILDER.pos(x2, cy, z2).tex(u0, v0).endVertex();
			BUILDER.pos(x2, cy + height, z2).tex(u1, v0).endVertex();

			BUILDER.pos(x, cy + height, z).tex(u1, v1).endVertex();
			BUILDER.pos(x, cy, z).tex(u0, v1).endVertex();
		}
		TESSELLATOR.draw();
	}
}
