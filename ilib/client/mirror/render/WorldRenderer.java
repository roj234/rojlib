package ilib.client.mirror.render;

import ilib.ClientProxy;
import ilib.ImpLib;
import ilib.client.RenderUtils;
import ilib.client.mirror.ClientHandler;
import ilib.client.mirror.Mirror;
import ilib.client.mirror.Portal;
import ilib.client.mirror.render.world.RenderGlobalProxy;
import ilib.client.misc.StencilBuf;
import ilib.misc.EntityTransform;
import org.lwjgl.opengl.GL11;
import roj.collect.WeakMyHashMap;
import roj.math.Mat4x3f;
import roj.math.Vec3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

import static ilib.ClientProxy.mc;
import static ilib.client.misc.ParticleEntityBlood.PARTICLE_TEXTURES;
import static net.minecraft.client.renderer.GlStateManager.*;

public class WorldRenderer {
	public static RenderGlobalProxy proxy = new RenderGlobalProxy(ClientProxy.mc);
	private static final WeakMyHashMap<World, RenderGlobalProxy> otherProxies = new WeakMyHashMap<World, RenderGlobalProxy>() {
		@Override
		protected void onEntryRemoved(RenderGlobalProxy v) {
			v.setWorldAndLoadRenderers(null);
		}
	};

	public static int renderLevel;
	private static int renderCount;
	private static int frameCount;

	private static final StencilBuf stencilBuf = new StencilBuf();

	@SubscribeEvent
	public static void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
		ImpLib.proxy.runAtMainThread(true, WorldRenderer::clearAndReset);
	}

	public static void clearAndReset() {
		renderLevel = 0;
		renderCount = 0;
		frameCount = 0;

		stencilBuf.delete();

		proxy.setWorldAndLoadRenderers(null);

		Iterator<WeakMyHashMap.Entry<RenderGlobalProxy>> itr = otherProxies.iterator();
		while (itr.hasNext()) {
			RenderGlobalProxy value = itr.next().getValue();
			if (value != null) value.setWorldAndLoadRenderers(null);
		}
		otherProxies.clear();

		ClientHandler.clearWorlds();
	}

	@SubscribeEvent
	public static void onRenderTick(TickEvent.RenderTickEvent event) {
		if (event.phase == TickEvent.Phase.START) {
			renderLevel = 0;
			renderCount = 0;
		}
	}

	public static void renderWorld(Portal portal, Entity rve, float[] off, float[] rot, float partialTick) {
		if (renderLevel <= Mirror.maxRecursion && renderCount < Mirror.maxRecursion && portal.hasPair()) {
			EntityRenderer er = mc.entityRenderer;
			er.disableLightmap();
			disableCull();
			disableTexture2D();
			disableLighting();

			GL11.glStencilMask(0xFF);

			if (renderLevel++ == 0) {
				GL11.glEnable(GL11.GL_STENCIL_TEST);
				stencilBuf.init();

				GL11.glClearStencil(0);
				clear(GL11.GL_STENCIL_BUFFER_BIT);
			}

			renderCount++;

			GL11.glStencilFunc(GL11.GL_ALWAYS, Mirror.minStencilLevel + renderLevel, 0xFF);
			GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, renderLevel == 1 ? GL11.GL_REPLACE : GL11.GL_INCR);

			colorMask(false, false, false, false);

			if (FMLClientHandler.instance().hasOptifine()) {
				Mirror.maxRenderDistanceChunks = 2;

				enableTexture2D();
				pushMatrix();
				scale(0.1f, 0.1f, 0.1f);
				RenderUtils.rotateToPlayer();
				GlStateManager.rotate(180, 0, 0, 1);

				pushMatrix();
				String str = "Optifine is not supported";
				translate(-mc.fontRenderer.getStringWidth(str) / 2, -mc.fontRenderer.FONT_HEIGHT - 2, 0);
				mc.fontRenderer.drawString(str, 0, 0, -1);
				popMatrix();

				pushMatrix();
				str = "不支持Optifine";
				translate(-mc.fontRenderer.getStringWidth(str) / 2, 0, 0);
				mc.fontRenderer.drawString(str, 0, 0, -1);
				popMatrix();

				popMatrix();
				disableTexture2D();
			} else {
				// 限制可渲染的区域
				portal.renderPlane(partialTick);
			}

			GL11.glStencilMask(0x00);
			GL11.glStencilFunc(GL11.GL_EQUAL, Mirror.minStencilLevel + renderLevel, 0xFF);
			GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

			depthFunc(GL11.GL_ALWAYS);
			colorMask(true, true, true, true);

			// reset depth buffer
			pushMatrix();
			loadIdentity();

			matrixMode(GL11.GL_PROJECTION);
			pushMatrix();

			er.updateFogColor(partialTick);
			float r = er.fogColorRed;
			float g = er.fogColorGreen;
			float b = er.fogColorBlue;
			color(r, g, b, 1);

			pushMatrix();
			loadIdentity();
			GL11.glBegin(GL11.GL_QUADS);
			GL11.glVertex3d(1, -1, 1);
			GL11.glVertex3d(1, 1, 1);
			GL11.glVertex3d(-1, 1, 1);
			GL11.glVertex3d(-1, -1, 1);
			GL11.glEnd();
			popMatrix();

			matrixMode(GL11.GL_MODELVIEW);
			popMatrix();
			pushMatrix();

			depthFunc(GL11.GL_LEQUAL);

			enableCull();
			enableTexture2D();

			try {
				drawWorld(mc, portal, rve, off, rot, partialTick);
			} catch (Throwable e) {
				e.printStackTrace();
			}

			matrixMode(GL11.GL_PROJECTION);
			popMatrix();

			matrixMode(GL11.GL_MODELVIEW);
			popMatrix();

			disableCull();
			disableTexture2D();
			colorMask(false, false, false, false);
			depthFunc(GL11.GL_ALWAYS);

			if (renderLevel > 1) {
				GL11.glStencilFunc(GL11.GL_ALWAYS, Mirror.minStencilLevel + renderLevel - 1, 0xFF);
				GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_DECR);
				GL11.glStencilMask(0xFF);
			}

			// 限制可渲染的区域
			portal.renderPlane(partialTick);

			if (renderLevel > 1) {
				GL11.glStencilMask(0x00);
				GL11.glStencilFunc(GL11.GL_EQUAL, Mirror.minStencilLevel + renderLevel - 1, 0xFF);
			}

			depthFunc(GL11.GL_LEQUAL);
			colorMask(true, true, true, true);
			enableTexture2D();
			enableCull();

			if (--renderLevel == 0) {
				GL11.glDisable(GL11.GL_STENCIL_TEST);
			}
		}
	}

	private static void drawWorld(Minecraft mc, Portal portal, Entity rve, float[] off, float[] rot, float partialTick) {
		double px = rve.lastTickPosX + (rve.posX - rve.lastTickPosX) * partialTick;
		double py = rve.lastTickPosY + (rve.posY - rve.lastTickPosY) * partialTick;
		double pz = rve.lastTickPosZ + (rve.posZ - rve.lastTickPosZ) * partialTick;

		BlockPos pos = portal.getPos();

		if (portal.getOneSideRender()) {
			AxisAlignedBB plane = portal.getPlane();
			double dx = (plane.maxX + plane.minX) / 2D - px;
			double dy = (plane.maxY + plane.minY) / 2D - py;
			double dz = (plane.maxZ + plane.minZ) / 2D - pz;

			switch (portal.getFaceOn()) {
				case NORTH:
					if (dz > 0) return;
					break;
				case SOUTH:
					if (dz < 0) return;
					break;
				case WEST:
					if (dx > 0) return;
					break;
				case EAST:
					if (dx < 0) return;
					break;
				case UP:
					if (dy < 0) return;
					break;
				case DOWN:
					if (dy > 0) return;
					break;
			}
		}

		Portal pair = portal.getPair();

		TileEntityRendererDispatcher.instance.drawBatch(MinecraftForgeClient.getRenderPass());

		color(1F, 1F, 1F);

		RenderGlobal global = mc.renderGlobal;
		RenderGlobalProxy proxy = getWorldRendererFor(mc.world = pair.getWorld());
		mc.renderGlobal = proxy;
		mc.renderChunksMany = false;

		proxy.cloudTickCounter = global.cloudTickCounter;
		proxy.bindViewFrustum(pair);
		proxy.storePlayerInfo();

		int chunk = mc.gameSettings.renderDistanceChunks;
		mc.gameSettings.renderDistanceChunks = portal.getRenderDistanceChunks();

		AxisAlignedBB plane = pair.getPlane();
		double dx = (plane.maxX + plane.minX) / 2D + off[0] - pos.getX();
		double dy = (plane.maxY + plane.minY) / 2D + off[1] - pos.getY();
		double dz = (plane.maxZ + plane.minZ) / 2D + off[2] - pos.getZ();

		if (rot[0] != 0 || rot[1] != 0) {
			Vec3f dir = new Vec3f((float) (px - pos.getX()), (float) (py - pos.getY()), (float) (pz - pos.getZ()));

			dx -= dir.x;
			dy -= dir.y;
			dz -= dir.z;

			Mat4x3f matrix = new Mat4x3f().rotateX((float) Math.toRadians(rot[1])).rotateY((float) Math.toRadians(-rot[0]));
			dir = (Vec3f) matrix.mul(dir);

			dx += dir.x;
			dy += dir.y;
			dz += dir.z;
			// todo process X-axis rotation
		}

		EntityTransform et = new EntityTransform(rve, dx, dy, dz, rot[0], rot[1]);
		et.apply();
		try {
			drawWorld(proxy, rve, portal, partialTick);
		} catch (Throwable e) {
			e.printStackTrace();
		}
		et.revert();

		proxy.unbindViewFrustum();

		mc.gameSettings.renderDistanceChunks = chunk;
		mc.renderGlobal = global;
		mc.world = global.world;

		ForgeHooksClient.setRenderPass(0);

		TileEntityRendererDispatcher.instance.prepare(mc.world, mc.getTextureManager(), mc.fontRenderer, rve, mc.objectMouseOver, partialTick);

		RenderManager rman = RenderUtils.RENDER_MANAGER;
		rman.cacheActiveRenderInfo(mc.world, mc.fontRenderer, rve, mc.pointedEntity, mc.gameSettings, partialTick);

		rman.renderPosX = px;
		TileEntityRendererDispatcher.staticPlayerX = px;
		Particle.interpPosX = px;

		rman.renderPosY = py;
		TileEntityRendererDispatcher.staticPlayerY = py;
		Particle.interpPosY = py;

		rman.renderPosZ = pz;
		TileEntityRendererDispatcher.staticPlayerZ = pz;
		Particle.interpPosZ = pz;

		Particle.cameraViewDir = rve.getLook(partialTick);

		TileEntityRendererDispatcher.instance.preDrawBatch();
	}

	private static void drawWorld(RenderGlobalProxy proxy, Entity rve, Portal portal, float partialTick) {
		mc.profiler.startSection("Portal");
		enableCull();

		double dx = rve.lastTickPosX + (rve.posX - rve.lastTickPosX) * (double) partialTick;
		double dy = rve.lastTickPosY + (rve.posY - rve.lastTickPosY) * (double) partialTick;
		double dz = rve.lastTickPosZ + (rve.posZ - rve.lastTickPosZ) * (double) partialTick;

		EntityRenderer er = mc.entityRenderer;

		GameSettings gs = mc.gameSettings;

		int chunk = gs.renderDistanceChunks;
		if (!portal.renderNearerFog()) gs.renderDistanceChunks = 16;
		er.setupCameraTransform(partialTick, 2);
		gs.renderDistanceChunks = chunk;

		ActiveRenderInfo.updateRenderInfo(mc.player, false);

		RenderHelper.disableStandardItemLighting();

		er.setupFog(0, partialTick);

		er.farPlaneDistance = chunk << 4;

		// 节省资源 Step2: 通过视锥限制渲染的《区块》范围
		ICamera cam = new MyFrustum(portal.getPair().getPlane(), rve);
		cam.setPosition(dx, dy, dz);

		if (chunk >= 4 && !FMLClientHandler.instance().hasOptifine()) {
			mc.profiler.startSection("Sky");
			proxy.renderSky(partialTick, 2);
			mc.profiler.endSection();
		}

		mc.profiler.startSection("Setup");

		shadeModel(GL11.GL_SMOOTH);

		if (rve.posY + (double) rve.getEyeHeight() < 128) {
			er.renderCloudsCheck(proxy, partialTick, 2, dx, dy, dz);
		}

		RenderUtils.bindMinecraftBlockSheet();

		RenderHelper.disableStandardItemLighting();

		proxy.setupTerrain(rve, partialTick, cam, frameCount++, mc.player.isSpectator());

		mc.profiler.endStartSection("Update");

		int fps = 60;
		long l = 1000000000 / fps / 4;
		proxy.updateChunks(System.nanoTime() + l);

		matrixMode(GL11.GL_MODELVIEW);
		pushMatrix();
		disableAlpha();
		proxy.renderBlockLayer(BlockRenderLayer.SOLID, partialTick, 2, rve);
		enableAlpha();
		proxy.renderBlockLayer(BlockRenderLayer.CUTOUT_MIPPED, partialTick, 2, rve);

		ITextureObject texBlock = mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

		texBlock.setBlurMipmap(false, false);
		proxy.renderBlockLayer(BlockRenderLayer.CUTOUT, partialTick, 2, rve);
		texBlock.restoreLastBlurMipmap();
		shadeModel(GL11.GL_FLAT);
		alphaFunc(GL11.GL_GREATER, 0.1F);

		mc.profiler.endStartSection("Entities(0)");

		RenderHelper.enableStandardItemLighting();
		ForgeHooksClient.setRenderPass(0);
		proxy.renderEntities(rve, cam, partialTick, portal);
		ForgeHooksClient.setRenderPass(0);
		RenderHelper.disableStandardItemLighting();
		er.disableLightmap();
		popMatrix();

		mc.profiler.endStartSection("BlkDmg");

		enableBlend();
		tryBlendFuncSeparate(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ONE, DestFactor.ZERO);
		texBlock.setBlurMipmap(false, false);
		proxy.drawBlockDamageTexture(RenderUtils.TESSELLATOR, RenderUtils.BUILDER, rve, partialTick);
		texBlock.restoreLastBlurMipmap();
		disableBlend();

		mc.profiler.endStartSection("ParticleMove");

		er.enableLightmap();
		er.setupFog(0, partialTick);

		mc.profiler.endStartSection("Particle");

		drawCulledParticle(rve, portal, partialTick, dx, dy, dz);

		er.disableLightmap();

		mc.profiler.endStartSection("Rain");

		depthMask(false);
		enableCull();
		er.renderRainSnow(partialTick);

		mc.profiler.endStartSection("WBorder");

		depthMask(true);
		proxy.renderWorldBorder(rve, partialTick);

		disableBlend();
		enableCull();
		tryBlendFuncSeparate(770, 771, 1, 0);
		alphaFunc(GL11.GL_GREATER, 0.1F);
		er.setupFog(0, partialTick);

		enableBlend();
		depthMask(false);
		RenderUtils.bindMinecraftBlockSheet();
		shadeModel(GL11.GL_SMOOTH);
		proxy.renderBlockLayer(BlockRenderLayer.TRANSLUCENT, partialTick, 2, rve);

		mc.profiler.endStartSection("Entities(1)");

		RenderHelper.enableStandardItemLighting();
		ForgeHooksClient.setRenderPass(1);
		proxy.renderEntities(rve, cam, partialTick, portal);

		ForgeHooksClient.setRenderPass(-1);
		RenderHelper.disableStandardItemLighting();

		shadeModel(GL11.GL_FLAT);
		depthMask(true);
		enableCull();
		disableBlend();
		disableFog();

		if (rve.posY + (double) rve.getEyeHeight() >= 128) {
			matrixMode(GL11.GL_PROJECTION);
			pushMatrix();

			matrixMode(GL11.GL_MODELVIEW);
			er.renderCloudsCheck(proxy, partialTick, 2, dx, dy, dz);

			matrixMode(GL11.GL_PROJECTION);
			popMatrix();

			matrixMode(GL11.GL_MODELVIEW);
		}

		ForgeHooksClient.dispatchRenderLast(proxy, partialTick);

		mc.entityRenderer.enableLightmap();

		mc.profiler.endSection();
		mc.profiler.endSection();
	}

	private static void drawCulledParticle(Entity rve, Portal portal, float partialTick, double dx, double dy, double dz) {
		Particle.interpPosX = dx;
		Particle.interpPosY = dy;
		Particle.interpPosZ = dz;
		Particle.cameraViewDir = rve.getLook(partialTick);
		float dt = 0.017453292F;
		float f = MathHelper.cos(rve.rotationYaw * dt);
		float f1 = MathHelper.sin(rve.rotationYaw * dt);
		float f2 = -f1 * MathHelper.sin(rve.rotationPitch * dt);
		float f3 = f * MathHelper.sin(rve.rotationPitch * dt);
		float f4 = MathHelper.cos(rve.rotationPitch * dt);

		ParticleManager pcman = mc.effectRenderer;

		Portal pair = portal.getPair();

		ArrayDeque<Particle>[][] layers = pcman.fxLayers;
		BufferBuilder bb = RenderUtils.BUILDER;
		for (int i = 0; i < 2; ++i) {
			Queue<Particle> queue = layers[3][i];

			if (!queue.isEmpty()) {
				for (Particle particle : queue) {
					if (!portal.getCullRender() || canDraw(particle, pair.getFaceOn(), pair.getPos())) {
						particle.renderParticle(bb, rve, partialTick, f, f4, f1, f2, f3);
					}
				}
			}
		}

		GlStateManager.enableBlend();
		GlStateManager.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA);
		GlStateManager.alphaFunc(516, 0.003921569F);

		for (int i = 0; i < 3; ++i) {
			for (int j = 0; j < 2; ++j) {
				ArrayDeque<Particle> queue = layers[i][j];
				if (!queue.isEmpty()) {
					switch (j) {
						case 0:
							GlStateManager.depthMask(false);
							break;
						case 1:
							GlStateManager.depthMask(true);
					}

					switch (i) {
						case 0:
						default:
							RenderUtils.bindTexture(PARTICLE_TEXTURES);
							break;
						case 1:
							RenderUtils.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
					}

					GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
					Tessellator tess = Tessellator.getInstance();
					bb.begin(7, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);

					for (Iterator<Particle> itr = queue.iterator(); itr.hasNext(); ) {
						Particle particle = itr.next();
						if (!portal.getCullRender() || canDraw(particle, pair.getFaceOn(), pair.getPos())) {
							try {
								particle.renderParticle(bb, rve, partialTick, f, f4, f1, f2, f3);
							} catch (Throwable e) {
								itr.remove();
							}
						}
					}

					tess.draw();
				}
			}
		}

		GlStateManager.depthMask(true);
		GlStateManager.disableBlend();
		GlStateManager.alphaFunc(516, 0.1F);
	}

	private static boolean canDraw(Particle ent, EnumFacing face, BlockPos pos) {
		switch (face) {
			case UP:
				AxisAlignedBB box = ent.getBoundingBox();
				return (box.maxY + box.minY) / 2D > pos.getY() - 1;
			case DOWN:
				box = ent.getBoundingBox();
				return (box.maxY + box.minY) / 2D < pos.getY() + 1;
			case NORTH:
				return ent.posZ < pos.getZ() + 1;
			case SOUTH:
				return ent.posZ > pos.getZ() - 1;
			case EAST:
				return ent.posX > pos.getX() - 1;
			case WEST:
				return ent.posX < pos.getX() + 1;
		}
		return false;
	}

	private static RenderGlobalProxy getWorldRendererFor(WorldClient world) {
		if (world == mc.world) {
			if (proxy.world != mc.world) proxy.setWorldAndLoadRenderers(mc.world);
			return proxy;
		}

		RenderGlobalProxy other = otherProxies.get(world);
		if (other == null) {
			otherProxies.put(world, other = new RenderGlobalProxy(mc));
			other.setWorldAndLoadRenderers(world);
		}
		return other;
	}

	public static void releaseResource(Portal portal) {
		WorldClient world = portal.getWorld();
		if (world == mc.world) {
			proxy.releaseViewFrustum(portal);
		}

		RenderGlobalProxy other = otherProxies.get(world);
		if (other != null) {
			other.releaseViewFrustum(portal);
		}
	}
}
