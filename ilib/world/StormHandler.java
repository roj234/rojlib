package ilib.world;

import ilib.ClientProxy;
import ilib.Config;
import ilib.client.RenderUtils;
import ilib.util.BlockHelper;
import org.lwjgl.opengl.GL11;
import roj.collect.RingBuffer;
import roj.math.Vec3d;
import roj.util.ArrayCache;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

/**
 * @author Roj234
 * @since 2022/4/4 21:38
 */
public abstract class StormHandler {
	protected static final Random rnd = new Random();
	static final RingBuffer<RainBlock> rainBlocks = new RingBuffer<>(512);

	private static final int PARTICLE_RADIUS = 20;
	private static final int RAIN_HEIGHT = 10;
	private static final float STEP = 0.0025F;

	public static class RainBlock extends BlockPos {
		int life;

		public RainBlock(BlockPos pos, int life) {
			super(pos);
			this.life = life;
		}

		protected void onRemoved() {}
	}

	protected static void addRainBlock(RainBlock rb) {
		RainBlock replaced = rainBlocks.ringAddLast(rb);
		if (null != replaced) {
			ClientProxy.mc.world.setBlockState(replaced, Blocks.AIR.getDefaultState());
		}
	}

	@SubscribeEvent
	public static void onTick(ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.END) return;

		WorldClient w = ClientProxy.mc.world;
		if (w != null && w.provider instanceof Climated && !ClientProxy.mc.isGamePaused()) {
			for (RainBlock rb : rainBlocks) {
				if (rb != null) {
					if (--rb.life == 0) {
						w.setBlockState(rb, Blocks.AIR.getDefaultState());
						rb.onRemoved();
					}
				}
			}
			if (!rainBlocks.isEmpty()) {
				// noinspection all
				while (rainBlocks.peekFirst().life == 0) rainBlocks.pollFirst();
			}

			Climated climate = (Climated) w.provider;

			if (climate.getStormHandler() != null) {
				StormHandler sp = climate.getStormHandler();
				// sp.update(w);
				sp.updateStrength(w);

				float strength = sp.updateLastStrength();

				if (strength > 0) {
					if (!ClientProxy.mc.gameSettings.fancyGraphics) {
						strength /= 2;
					}

					int particle = ClientProxy.mc.gameSettings.particleSetting;
					if (particle == 1) {
						strength /= 4;
					} else if (particle == 2) {
						return;
					}

					if (strength > 0) {
						Entity entity = ClientProxy.mc.getRenderViewEntity();

						int pass = (int) (20 * strength * strength);
						if (pass > 1234) pass = 1234;

						BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();
						Random rnd = StormHandler.rnd;
						rnd.setSeed(System.currentTimeMillis());

						int spawned = 0;
						double x = 0;
						double y = 0;
						double z = 0;
						for (int i = 0; i < pass; ++i) {
							// noinspection all
							pos.setPos(entity.posX + rnd.nextInt(PARTICLE_RADIUS) - rnd.nextInt(PARTICLE_RADIUS), entity.posY,
									   entity.posZ + rnd.nextInt(PARTICLE_RADIUS) - rnd.nextInt(PARTICLE_RADIUS));
							pos.setY(BlockHelper.getSurfaceBlockY(w, pos.getX(), pos.getZ()));

							if (pos.getY() <= entity.posY + PARTICLE_RADIUS && pos.getY() >= entity.posY - PARTICLE_RADIUS && sp.isVisibleIn(w.getBiome(pos))) {
								IBlockState state = w.getBlockState(pos);

								if (state.getMaterial() != Material.LAVA && state.getBlock() != Blocks.MAGMA) {
									AxisAlignedBB box = state.getBoundingBox(w, pos);

									double pX = pos.getX() + rnd.nextDouble();
									double pY = pos.getY() + 0.05F + box.maxY;
									double pZ = pos.getZ() + rnd.nextDouble();

									if (rnd.nextInt(++spawned) == 0) {
										x = pX;
										y = pY - 1;
										z = pZ;
										sp.spawnDeco(w, pos, state);
									}

									if (spawned < Config.maxParticleCountPerLayer / 4) sp.spawnParticle(w, pX, pY, pZ);
								}
							}
						}

						if (spawned > 0 && rnd.nextInt(3) < sp.rainSoundCounter++) {
							sp.rainSoundCounter = 0;

							if (y > (entity.posY + 1) && w.getPrecipitationHeight(pos.setPos(entity)).getY() > MathHelper.floor(entity.posY)) {
								sp.playStormSoundAbove(w, x, y, z);
							} else {
								sp.playStormSound(w, x, y, z);
							}
						}

						pos.release();
					}
				}
			}
		} else {
			rainBlocks.clear();
		}
	}

	protected float updateLastStrength() {
		float str = lastStrength;
		float nowStr = strength;

		if (str < nowStr) {
			lastStrength = str + STEP;
		} else if (str > nowStr) {
			lastStrength = str - STEP;
		}

		if (Math.abs(str - nowStr) < STEP) {
			lastStrength = nowStr;
		}
		return str;
	}

	@SubscribeEvent
	public static void onUpdate(WorldTickEvent event) {
		if (event.phase == TickEvent.Phase.START) return;

		// 该事件不会在客户端触发
		if (event.world.provider instanceof Climated) {
			Climated climate = (Climated) event.world.provider;
			StormHandler sh = climate.getStormHandler();
			if (sh == null) return;

			sh.updateLastStrength();
			sh.update(event.world);
		}
	}

	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public static void onRender(RenderWorldLastEvent event) {
		WorldClient w = ClientProxy.mc.world;
		if (w.provider instanceof Climated) {
			StormHandler sh = ((Climated) w.provider).getStormHandler();
			if (sh == null) return;

			GlStateManager.pushMatrix();

			float pt = event.getPartialTicks();

			Entity e = ClientProxy.mc.getRenderViewEntity();
			// noinspection all
			double x = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt;
			double y = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt;
			double z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt;
			GlStateManager.translate(-x, -y, -z);

			sh.render(pt, w);

			GlStateManager.popMatrix();
		}
	}

	@SideOnly(Side.CLIENT)
	protected void render(float partialTicks, WorldClient world) {
		if (lastStrength == 0) return;

		if (doRainLight(world)) GlStateManager.enableLighting();

		Entity e = ClientProxy.mc.getRenderViewEntity();
		// noinspection all
		int posX = MathHelper.floor(e.posX);
		int posY = MathHelper.floor(e.posY);
		int posZ = MathHelper.floor(e.posZ);

		GlStateManager.enableBlend();
		RenderUtils.colorWhite();
		GlStateManager.enableColorMaterial();
		GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

		int r = ClientProxy.mc.gameSettings.fancyGraphics ? 12 : 6;

		BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();
		Vec3d tmp = new Vec3d();

		double t = (System.currentTimeMillis() % 100000 / 100000f);

		boolean render = false;
		for (int z = posZ - r; z <= posZ + r; ++z) {
			for (int x = posX - r; x <= posX + r; ++x) {
				Biome biome = world.getBiome(pos.setPos(x, 0, z));
				if (isVisibleIn(biome)) {
					int y = BlockHelper.getSurfaceBlockY(world, x, z);
					if (y > posY) continue;

					int minY = posY - RAIN_HEIGHT;
					if (minY < y) minY = y;

					int maxY = posY + RAIN_HEIGHT;
					if (maxY < y) maxY = y;

					BufferBuilder bb = RenderUtils.BUILDER;
					if (!render) {
						render = true;

						RenderUtils.bindTexture(getRainTexture(world, biome));
						bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_NORMAL);
					}

					rnd.setSeed(x * x * 123456 + x * 233 ^ z * z * 789001 + z * 322);

					double rx = (rnd.nextDouble() - 0.5) / 2;
					double rz = (rnd.nextDouble() - 0.5);

					getStormVelocity(world, pos, tmp).mul(lastStrength);

					int light = world.getCombinedLight(pos.setPos(x, y, z), 0);
					int sky = light >> 16 & 0xFFFF;
					light &= 0xFFFF;

					for (int i = (int) Math.floor(rnd.nextGaussian() * lastStrength); i >= 0; i--) {
						Vec3d wind = (Vec3d) this.tmp.set(tmp).mul(t * rnd.nextDouble());

						double dx = wind.x + (rnd.nextFloat() - 0.5f) * 2;
						double dy = wind.y * t * 100 - t * 200;
						double dz = wind.z + (rnd.nextFloat() - 0.5f) * 2;

						bb.pos(x - rx + dx, minY, z - rz + dz).tex(0, maxY * 0.25D + dy).lightmap(sky, light).endVertex();
						bb.pos(x + rx + dx, minY, z + rz + dz).tex(1, maxY * 0.25D + dy).lightmap(sky, light).endVertex();
						bb.pos(x + rx + dx - tmp.x, maxY, z + rz + dz - tmp.z).tex(1, minY * 0.25D + dy).lightmap(sky, light).endVertex();
						bb.pos(x - rx + dx - tmp.x, maxY, z - rz + dz - tmp.z).tex(0, minY * 0.25D + dy).lightmap(sky, light).endVertex();
					}
				}
			}
		}

		pos.release();

		if (render) RenderUtils.TESSELLATOR.draw();

		GlStateManager.disableBlend();
		GlStateManager.alphaFunc(516, 0.1F);
		GlStateManager.disableLighting();
	}

	public Vec3d tmp = new Vec3d();

	public float lastStrength;
	public float strength;

	private int rainSoundCounter;

	protected void update(World world) {
		if (lastStrength > 0) {
			List<Entity> entities = world.loadedEntityList;
			Vec3d tmp = new Vec3d();
			BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();

			for (int i = 0; i < entities.size(); i++) {
				Entity entity = entities.get(i);
				if (!(entity instanceof EntityLivingBase)) continue;
				if (BlockHelper.getSurfaceBlockY(world, (int) (entity.posX + 0.5), (int) (entity.posZ + 0.5)) > entity.posY) continue;
				entity.setFire(0);
				Vec3d vec = getEntityVelocity(entity, pos.setPos(entity), tmp);
				if (vec != null && vec.len2() > 1e-5) {
					entity.motionX += vec.x;
					entity.motionY += vec.y;
					entity.motionZ += vec.z;

					if (!entity.velocityChanged) entity.velocityChanged = true;

					if (vec.y > 1e-3) entity.fallDistance *= 0.8f;
				}
			}

			pos.release();
		}
	}

	public abstract void updateStrength(World world);

	protected void spawnDeco(WorldClient w, BlockPos.MutableBlockPos pos, IBlockState state) {
		if (rnd.nextDouble() >= 0.003 * lastStrength) return;

		boolean solid = state.isSideSolid(w, pos, EnumFacing.UP);
		if (!solid) {
			AxisAlignedBB box = state.getCollisionBoundingBox(w, pos);
			solid = box != null && box.minX == 0 && box.maxX == 1 && box.minZ == 0 && box.maxZ == 1 && box.maxY == 1;
		}

		if (solid) {
			state = w.getBlockState(pos.move(EnumFacing.UP));
			if (state.getBlock().isAir(state, w, pos)) {
				w.setBlockState(pos, Blocks.WATER.getStateFromMeta(7));
				addRainBlock(new RainBlock(pos, 20 * 20 + rnd.nextInt(100)));
			}
		}
	}

	public Vec3d getEntityVelocity(Entity entity, BlockPos.MutableBlockPos pos, Vec3d dest) {
		dest = getStormVelocity(entity.world, pos, dest);
		if (dest == null) return null;
		AxisAlignedBB box = entity.getEntityBoundingBox();

		double region = (box.maxX - box.minX);
		region *= region;
		double t = box.maxY - box.minY;
		region += t * t;
		t = box.maxZ - box.minZ;
		region += t * t;

		dest.mul(1 / region).mul(lastStrength * lastStrength / 50 / ((entity.motionX * entity.motionX + entity.motionY * entity.motionY + entity.motionZ * entity.motionZ) + 10));

		if (entity instanceof EntityPlayer) {
			EntityPlayer player = (EntityPlayer) entity;

			if (player.isCreative() || player.isSpectator()) {
				return null;
			}

			return player.isSneaking() && player.onGround ? (Vec3d) dest.mul(0.7) : dest;
		}

		return dest;
	}

	public boolean isVisibleIn(Biome biome) {
		return biome.getRainfall() > 0;
	}

	public boolean doRainLight(World world) {
		return world.provider.hasSkyLight();
	}

	/**
	 * pos为null时返回平均风速
	 */
	public abstract Vec3d getStormVelocity(World world, @Nullable BlockPos pos, Vec3d dest);

	@SideOnly(Side.CLIENT)
	public ResourceLocation getCloudTexture(World world) {
		return new ResourceLocation("textures/environment/clouds.png");
	}

	@SideOnly(Side.CLIENT)
	protected ResourceLocation getRainTexture(World world, Biome biome) {
		return new ResourceLocation("textures/environment/rain.png");
	}

	protected void spawnParticle(World world, double pX, double pY, double pZ) {
		if (rnd.nextDouble() > 0.85) {world.spawnParticle(EnumParticleTypes.DRIP_WATER, pX, pY, pZ, 0, 1, 0, ArrayCache.INTS);} else if (rnd.nextDouble() > 0.6)
			world.spawnParticle(EnumParticleTypes.WATER_DROP, pX, pY, pZ, 0, 1, 0, ArrayCache.INTS);
	}

	protected void playStormSoundAbove(World world, double x, double y, double z) {
		world.playSound(x, y, z, SoundEvents.WEATHER_RAIN_ABOVE, SoundCategory.WEATHER, 0.1F, 0.5F, false);
	}

	protected void playStormSound(World world, double x, double y, double z) {
		world.playSound(x, y, z, SoundEvents.WEATHER_RAIN, SoundCategory.WEATHER, 0.2F, 1.0F, false);
	}
}
