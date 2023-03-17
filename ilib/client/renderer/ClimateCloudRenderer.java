package ilib.client.renderer;

import ilib.ClientProxy;
import ilib.client.RenderUtils;
import ilib.world.StormHandler;
import org.lwjgl.opengl.GL11;
import roj.math.Vec3d;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

import net.minecraftforge.client.IRenderHandler;

/**
 * @author solo6975
 * @since 2022/4/5 15:35
 */
public class ClimateCloudRenderer extends IRenderHandler {
	private final StormHandler sh;
	private final Vec3d tmp = new Vec3d();

	public ClimateCloudRenderer(StormHandler sh) {
		this.sh = sh;
	}

	protected static float ceilToScale(float scale, float value) {
		return MathHelper.ceil(value / scale) * scale;
	}

	protected static void vertices(float scale, BufferBuilder bb) {
		boolean fancy = ClientProxy.mc.gameSettings.clouds == 2;
		float CULL_DIST = 2.0F * scale;
		float cl = fancy ? 0.7F : 1.0F;
		float sectEnd = ceilToScale(scale, ClientProxy.mc.gameSettings.renderDistanceChunks * 32);
		float sectStep = ceilToScale(scale, sectEnd * 2.0F / 12.0F);
		float sectPx = 0.00390625F / scale;

		float x0 = -sectEnd;
		for (float x1 = -sectEnd; x1 < sectEnd; x0 = x1) {
			x1 += sectStep;
			if (x1 > sectEnd) {
				x1 = sectEnd;
			}

			float z0 = -sectEnd;
			for (float z1 = -sectEnd; z1 < sectEnd; z0 = z1) {
				z1 += sectStep;
				if (z1 > sectEnd) {
					z1 = sectEnd;
				}

				float u0 = x0 * sectPx;
				float u1 = x1 * sectPx;
				float v0 = z0 * sectPx;
				float v1 = z1 * sectPx;
				bb.pos(x0, 0.0D, z0).tex(u0, v0).color(cl, cl, cl, 0.8F).endVertex();
				bb.pos(x1, 0.0D, z0).tex(u1, v0).color(cl, cl, cl, 0.8F).endVertex();
				bb.pos(x1, 0.0D, z1).tex(u1, v1).color(cl, cl, cl, 0.8F).endVertex();
				bb.pos(x0, 0.0D, z1).tex(u0, v1).color(cl, cl, cl, 0.8F).endVertex();
				if (fancy) {
					bb.pos(x0, 4.0D, z0).tex(u0, v0).color(1.0F, 1.0F, 1.0F, 0.8F).endVertex();
					bb.pos(x0, 4.0D, z1).tex(u0, v1).color(1.0F, 1.0F, 1.0F, 0.8F).endVertex();
					bb.pos(x1, 4.0D, z1).tex(u1, v1).color(1.0F, 1.0F, 1.0F, 0.8F).endVertex();
					bb.pos(x1, 4.0D, z0).tex(u1, v0).color(1.0F, 1.0F, 1.0F, 0.8F).endVertex();
					float slice = x0;

					float slice0;
					float slice1;
					while (slice < x1) {
						slice0 = slice * sectPx;
						slice1 = slice0 + 0.00390625F;
						if (slice > -CULL_DIST) {
							slice += 0.001F;
							bb.pos(slice, 0.0D, z1).tex(slice0, v1).color(0.9F, 0.9F, 0.9F, 0.8F).endVertex();
							bb.pos(slice, 4.0D, z1).tex(slice1, v1).color(0.9F, 0.9F, 0.9F, 0.8F).endVertex();
							bb.pos(slice, 4.0D, z0).tex(slice1, v0).color(0.9F, 0.9F, 0.9F, 0.8F).endVertex();
							bb.pos(slice, 0.0D, z0).tex(slice0, v0).color(0.9F, 0.9F, 0.9F, 0.8F).endVertex();
							slice -= 0.001F;
						}

						slice += scale;
						if (slice <= CULL_DIST) {
							slice -= 0.001F;
							bb.pos(slice, 0.0D, z0).tex(slice0, v0).color(0.9F, 0.9F, 0.9F, 0.8F).endVertex();
							bb.pos(slice, 4.0D, z0).tex(slice1, v0).color(0.9F, 0.9F, 0.9F, 0.8F).endVertex();
							bb.pos(slice, 4.0D, z1).tex(slice1, v1).color(0.9F, 0.9F, 0.9F, 0.8F).endVertex();
							bb.pos(slice, 0.0D, z1).tex(slice0, v1).color(0.9F, 0.9F, 0.9F, 0.8F).endVertex();
							slice += 0.001F;
						}
					}

					slice = z0;

					while (slice < z1) {
						slice0 = slice * sectPx;
						slice1 = slice0 + 0.00390625F;
						if (slice > -CULL_DIST) {
							slice += 0.001F;
							bb.pos(x0, 0.0D, slice).tex(u0, slice0).color(0.8F, 0.8F, 0.8F, 0.8F).endVertex();
							bb.pos(x0, 4.0D, slice).tex(u0, slice1).color(0.8F, 0.8F, 0.8F, 0.8F).endVertex();
							bb.pos(x1, 4.0D, slice).tex(u1, slice1).color(0.8F, 0.8F, 0.8F, 0.8F).endVertex();
							bb.pos(x1, 0.0D, slice).tex(u1, slice0).color(0.8F, 0.8F, 0.8F, 0.8F).endVertex();
							slice -= 0.001F;
						}

						slice += scale;
						if (slice <= CULL_DIST) {
							slice -= 0.001F;
							bb.pos(x1, 0.0D, slice).tex(u1, slice0).color(0.8F, 0.8F, 0.8F, 0.8F).endVertex();
							bb.pos(x1, 4.0D, slice).tex(u1, slice1).color(0.8F, 0.8F, 0.8F, 0.8F).endVertex();
							bb.pos(x0, 4.0D, slice).tex(u0, slice1).color(0.8F, 0.8F, 0.8F, 0.8F).endVertex();
							bb.pos(x0, 0.0D, slice).tex(u0, slice0).color(0.8F, 0.8F, 0.8F, 0.8F).endVertex();
							slice += 0.001F;
						}
					}
				}
			}
		}
	}

	protected int getScale() {
		return 1;
	}

	@Override
	public void render(float partialTicks, WorldClient world, Minecraft mc) {
		float dd = (System.currentTimeMillis() % 240000) / 24000f;
		Vec3d vec = (Vec3d) sh.getStormVelocity(world, null, tmp).mul(sh.lastStrength + 1).mul(12).mul(dd);

		Entity p = mc.getRenderViewEntity();
		double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * partialTicks;

		GlStateManager.translate(vec.x, -y + world.provider.getCloudHeight() + 0.33F, vec.z);

		RenderUtils.bindTexture(sh.getCloudTexture(world));

		GlStateManager.disableLighting();
		GlStateManager.disableCull();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

		net.minecraft.util.math.Vec3d c = world.provider.getCloudColor(partialTicks);

		GlStateManager.scale(12, 1, 12);
		GlStateManager.color((float) c.x, (float) c.y, (float) c.z, 1);

		BufferBuilder bb = RenderUtils.BUILDER;
		bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
		vertices(getScale(), bb);
		RenderUtils.TESSELLATOR.draw();

		RenderUtils.colorWhite();
		GlStateManager.disableBlend();
		GlStateManager.enableCull();
	}
}
