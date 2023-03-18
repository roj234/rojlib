package ilib.asm.nx.client;

import ilib.Config;
import ilib.asm.util.MCHooksClient;
import ilib.client.mirror.render.world.RenderGlobalProxy;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.collect.BSLowHeap;
import roj.math.MathUtils;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.DestroyBlockProgress;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import net.minecraftforge.client.MinecraftForgeClient;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2020/9/24 13:05
 */
@Nixim("net.minecraft.client.renderer.RenderGlobal")
class NiximRenderGlobal extends RenderGlobal {
	@Copy(unique = true)
	long threshold;
	@Copy(unique = true)
	boolean lastSkipped;
	@Copy(unique = true)
	BSLowHeap<Entity> byPos2;

	public NiximRenderGlobal(Minecraft mcIn) {
		super(mcIn);
	}

	@Inject("func_180446_a")
	@Override
	public void renderEntities(Entity view, ICamera camera, float partialTicks) {
		int pass = MinecraftForgeClient.getRenderPass();
		if (pass <= 0) {
			threshold = Config.minimumFPS == 0 ? 0 : System.nanoTime() + (1000_000_000L / Config.minimumFPS);
		}

		if (this.renderEntitiesStartupCounter > 0) {
			if (pass <= 0) {
				--this.renderEntitiesStartupCounter;
			}
		} else {
			this.world.profiler.startSection("prepare");

			Entity self = this.mc.getRenderViewEntity();

			TileEntityRendererDispatcher.instance.prepare(this.world, this.mc.getTextureManager(), this.mc.fontRenderer, self, this.mc.objectMouseOver, partialTicks);

			this.renderManager.cacheActiveRenderInfo(this.world, this.mc.fontRenderer, self, this.mc.pointedEntity, this.mc.gameSettings, partialTicks);

			if (pass == 0) {
				MCHooksClient.resetEntityCuller();
				this.countEntitiesTotal = 0;
				this.countEntitiesRendered = 0;
				this.countEntitiesHidden = 0;
			}

			double ex = MathUtils.interpolate(self.lastTickPosX, self.posX, partialTicks);
			double ey = MathUtils.interpolate(self.lastTickPosY, self.posY, partialTicks);
			double ez = MathUtils.interpolate(self.lastTickPosZ, self.posZ, partialTicks);

			this.renderManager.setRenderPosition(TileEntityRendererDispatcher.staticPlayerX = ex, TileEntityRendererDispatcher.staticPlayerY = ey, TileEntityRendererDispatcher.staticPlayerZ = ez);

			this.mc.entityRenderer.enableLightmap();

			renderEntities(camera, partialTicks, pass, self, view);
			RenderGlobalProxy.renderTESR(this, camera, partialTicks, pass, threshold);

			this.world.profiler.endStartSection("damagedBlocks");

			this.preRenderDamagedBlocks();

			for (DestroyBlockProgress progress : this.damagedBlocks.values()) {
				BlockPos pos = progress.getPosition();
				TileEntity tile = this.world.getTileEntity(pos);
				if (tile != null) {
					if (tile instanceof TileEntityChest) {
						TileEntityChest chest = (TileEntityChest) tile;
						if (chest.adjacentChestXNeg != null) {
							pos = pos.offset(EnumFacing.WEST);
							tile = this.world.getTileEntity(pos);
						} else if (chest.adjacentChestZNeg != null) {
							pos = pos.offset(EnumFacing.NORTH);
							tile = this.world.getTileEntity(pos);
						}
					}

					IBlockState state = this.world.getBlockState(pos);
					if (tile != null && state.hasCustomBreakingProgress()) {
						TileEntityRendererDispatcher.instance.render(tile, partialTicks, progress.getPartialBlockDamage());
					}
				}
			}

			this.postRenderDamagedBlocks();
			this.mc.entityRenderer.disableLightmap();
			this.mc.profiler.endSection();
		}
	}

	@Copy
	public void renderEntities(ICamera camera, float partialTicks, int pass, Entity self, Entity viewing) {
		this.world.profiler.endStartSection("global");
		if (pass == 0) {
			this.countEntitiesTotal = this.world.getLoadedEntityList().size();
		}

		double dx = MathUtils.interpolate(viewing.prevPosX, viewing.posX, partialTicks);
		double dy = MathUtils.interpolate(viewing.prevPosY, viewing.posY, partialTicks);
		double dz = MathUtils.interpolate(viewing.prevPosZ, viewing.posZ, partialTicks);

		RenderManager rm = this.renderManager;

		List<Entity> weatherEffects = this.world.weatherEffects;
		for (int i = 0; i < weatherEffects.size(); ++i) {
			Entity eff = weatherEffects.get(i);
			if (eff.shouldRenderInPass(pass)) {
				++this.countEntitiesRendered;
				if (eff.isInRangeToRender3d(dx, dy, dz)) {
					rm.renderEntityStatic(eff, partialTicks, false);
				}
			}
		}

		this.world.profiler.endStartSection("entities");

		boolean doOutline = pass == 0 && this.isRenderEntityOutlines();

		List<Entity> outline = doOutline ? new ArrayList<>() : null;
		List<Entity> multipass = new ArrayList<>();

		boolean renderSelf = this.mc.gameSettings.thirdPersonView != 0 || (viewing instanceof EntityLivingBase && ((EntityLivingBase) viewing).isPlayerSleeping());

		EntityPlayerSP p = this.mc.player;

		List<Entity> list = world.loadedEntityList;

		if (lastSkipped) {
			// 统计表明, BSLowHeap比TreeMap快一倍
			// 而且不会新建Entry没有GC开销
			if (byPos2 == null) {
				byPos2 = new BSLowHeap<>((o1, o2) -> {
					int v = Double.compare(o1.getDistanceSq(viewing), o2.getDistanceSq(viewing));
					return v != 0 ? v : Integer.compare(o1.getEntityId(), o2.getEntityId());
				});
			} else {byPos2.clear();}

			BSLowHeap<Entity> bp = byPos2;
			for (int i = 0; i < list.size(); i++) {
				bp.add(list.get(i));
			}

			list.clear();
			for (int i = 0; i < bp.size(); i++) {
				list.add(bp.get(i));
			}

			lastSkipped = false;
		}

		long t = this.threshold;

		for (int i = 0; i < list.size(); i++) {
			Entity entity = list.get(i);
			if (entity.shouldRenderInPass(pass)) {
				if (t != 0 && System.nanoTime() > t) {
					countEntitiesTotal = -1;
					lastSkipped = true;
					multipass.clear();
					break;
				}

				if (rm.shouldRender(entity, camera, dx, dy, dz) || entity.isRidingOrBeingRiddenBy(p)) {
					if (entity != self || renderSelf) {
						if (doOutline && isOutlineActive(entity, self, camera)) {
							outline.add(entity);
						}

						if (Config.entityCulling && MCHooksClient.culledBy(entity, viewing)) {
							countEntitiesHidden++;

							MCHooksClient.rpl_renderNameTag(partialTicks, rm, entity);
						} else {
							countEntitiesRendered++;

							rm.renderEntityStatic(entity, partialTicks, false);

							if (rm.isRenderMultipass(entity)) {
								multipass.add(entity);
							}
						}
					}
				}
			}
		}

		if (!multipass.isEmpty()) {
			for (int i = multipass.size() - 1; i >= 0; i--) {
				rm.renderMultipass(multipass.get(i), partialTicks);
			}
		}

		if (doOutline && (!outline.isEmpty() || this.entityOutlinesRendered)) {
			this.world.profiler.endStartSection("entityOutlines");
			this.entityOutlineFramebuffer.framebufferClear();
			this.entityOutlinesRendered = !outline.isEmpty();
			if (!outline.isEmpty()) {
				GlStateManager.depthFunc(519);
				GlStateManager.disableFog();
				this.entityOutlineFramebuffer.bindFramebuffer(false);
				RenderHelper.disableStandardItemLighting();
				rm.setRenderOutlines(true);

				for (int j = outline.size() - 1; j >= 0; --j) {
					rm.renderEntityStatic(outline.get(j), partialTicks, false);
				}

				rm.setRenderOutlines(false);
				RenderHelper.enableStandardItemLighting();
				GlStateManager.depthMask(false);
				this.entityOutlineShader.render(partialTicks);
				GlStateManager.enableLighting();
				GlStateManager.depthMask(true);
				GlStateManager.enableFog();
				GlStateManager.enableBlend();
				GlStateManager.enableColorMaterial();
				GlStateManager.depthFunc(515);
				GlStateManager.enableDepth();
				GlStateManager.enableAlpha();
			}

			this.mc.getFramebuffer().bindFramebuffer(false);
		}
	}
}
