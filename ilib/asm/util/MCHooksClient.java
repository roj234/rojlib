package ilib.asm.util;

import ilib.ClientProxy;
import ilib.Config;
import ilib.client.mirror.render.world.MyVisGraph;
import ilib.misc.MutAABB;
import ilib.util.ColorUtil;
import ilib.util.Reflection;
import ilib.util.ReflectionClient;
import org.apache.commons.lang3.mutable.MutableLong;
import org.apache.commons.lang3.tuple.MutablePair;
import org.lwjgl.BufferUtils;
import roj.collect.Int2IntMap;
import roj.collect.LRUCache;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.io.NIOUtil;
import roj.reflect.FieldAccessor;
import roj.reflect.ReflectionUtils;
import roj.util.ByteList;
import roj.util.EmptyArrays;
import roj.util.Helpers;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.crash.CrashReport;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import net.minecraftforge.event.terraingen.BiomeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Roj233
 * @since 2022/4/22 17:25
 */
@SideOnly(Side.CLIENT)
public class MCHooksClient extends BlockPos.MutableBlockPos {
	public final List<BakedQuad>[] tmpArray = Helpers.cast(new List<?>[7]);

	// region 音频缓冲

	private ByteBuffer audioBuf;
	private IntBuffer audioBuf2 = BufferUtils.createIntBuffer(8);

	public static ByteBuffer createByteBuffer(int length) {
		MCHooksClient $ = get();

		ByteBuffer buf = $.audioBuf;
		if (buf == null || buf.capacity() < length) {
			if (buf != null) NIOUtil.clean(buf);
			$.audioBuf = buf = ByteBuffer.allocateDirect(length).order(ByteOrder.nativeOrder());
		}
		return (ByteBuffer) buf.clear().limit(length);
	}

	public static IntBuffer createIntBuffer(int length) {
		MCHooksClient $ = get();

		IntBuffer buf = $.audioBuf2;
		if (buf.capacity() < length) {
			NIOUtil.clean(buf);
			$.audioBuf2 = buf = BufferUtils.createIntBuffer(length);
		}
		return (IntBuffer) buf.clear().limit(length);
	}

	// endregion

	public static boolean debugRenderAllSide;

	// region 可复用对象缓存

	public static final BlockRenderLayer[] BlockRenderLayerValues = BlockRenderLayer.values();

	public static BlockRenderLayer[] values() {
		return BlockRenderLayerValues;
	}

	private static final ThreadLocal<MCHooksClient> myPos = ThreadLocal.withInitial(MCHooksClient::new);
	private static MCHooksClient curr;

	public static MCHooksClient get() {
		Thread t = Thread.currentThread();
		MCHooksClient p = MCHooksClient.curr;
		if (p == null || p.owner != t) {
			return curr = myPos.get();
		}
		return p;
	}

	public final Thread owner = Thread.currentThread();

	public final MutablePair<?, ?> pair = MutablePair.of(null, null);
	public final float[] data = new float[4];
	public final float[] data2 = new float[6];
	public final float[][] normals = new float[4][4];
	public float[] data3 = EmptyArrays.FLOATS;
	public int[] data4;

	public final MyVisGraph graph = new MyVisGraph();

	public static final MutAABB box1 = new MutAABB();
	public static final Vec3d a = new Vec3d(0, 0, 0);
	public static final Vec3d b = new Vec3d(0, 0, 0);
	private static final Vec3d c = new Vec3d(0, 0, 0);

	// endregion
	// region BiomeColor

	private final LRUCache<MutableLong, byte[]> rawColor = new LRUCache<>(Config.biomeBlendCache);
	private final LRUCache<MutableLong, byte[]> blendColor = new LRUCache<>(Config.biomeBlendCache);
	private final MutableLong colorCheck = new MutableLong();
	public static final int FOLIAGE = 0, GRASS = 1, WATER = 2;

	public int getColorMultiplier(IBlockAccess world, BlockPos pos, int kind) {
		colorCheck.setValue((((pos.getX() >>> 4) & 0xFFFFFFFFL) << 32) | ((pos.getZ() >>> 4) & 0xFFFFFFFFL));
		byte[] data = rawColor.get(colorCheck);
		if (data == null) {
			rawColor.put(new MutableLong(colorCheck.longValue()), data = new byte[256 * 3 * 3]);

			ByteList bb = IOUtil.SharedCoder.get().wrap(data);
			bb.clear();
			PooledMutableBlockPos pmb = PooledMutableBlockPos.retain();
			int x = pos.getX() & (~0xF);
			int z = pos.getZ() & (~0xF);
			for (int i = 0; i < 256; i++) {
				Biome b = world.getBiome(pmb.setPos(x + (i >> 4), 64, z + (i & 0xF)));
				bb.putMedium(b.getFoliageColorAtPos(pmb))
				  .putMedium(b.getGrassColorAtPos(pmb))
				  .putMedium(b.getWaterColor());
			}

			pmb.release();
		}

		int offset = 9 * (((pos.getX() & 0xF) << 4) | (pos.getZ() & 0xF)) + kind * 3;
		return ((data[offset++] & 0xFF) << 16) | ((data[offset++] & 0xFF) << 8) | (data[offset] & 0xFF);
	}

	public int getBlendedColorMultiplier(IBlockAccess world, BlockPos pos, int kind, int radius) {
		colorCheck.setValue((((pos.getX() >>> 4) & 0xFFFFFFFFL) << 32) | ((pos.getZ() >>> 4) & 0xFFFFFFFFL));

		byte[] data = blendColor.get(colorCheck);
		int offset = 9 * (((pos.getX() & 0xF) << 4) | (pos.getZ() & 0xF)) + kind * 3;

		blend:
		if (data == null || !isBlended(data, offset)) {
			if (data == null) {
				blendColor.put(new MutableLong(colorCheck.longValue()), data = new byte[256 * 3 * 3]);
			}

			if (Config.biomeBlendFabulously) {
				double distanceSq = pos.distanceSq(BlockPos.ORIGIN);
				int rgb = MathHelper.hsvToRGB((float) (distanceSq / 100 % 1000 / 1000), 0.7f, 0.8f);
				data[offset] = (byte) (rgb >> 16);
				data[offset + 1] = (byte) (rgb >> 8);
				data[offset + 2] = (byte) (rgb);
				break blend;
			}
			PooledMutableBlockPos pmb = PooledMutableBlockPos.retain();
			int ex = pos.getX() + radius;
			int sz = pos.getZ() - radius;
			int ez = pos.getZ() + radius;
			float linearR = 0, linearG = 0, linearB = 0;
			for (int x = pos.getX() - radius; x < ex; x++) {
				for (int z = sz; z < ez; z++) {
					int c = getColorMultiplier(world, pmb.setPos(x, 64, z), kind);
					linearR += ColorUtil.sRGBbyteToLinear((c & 0xFF0000) >> 16);
					linearG += ColorUtil.sRGBbyteToLinear((c & 0xFF00) >> 8);
					linearB += ColorUtil.sRGBbyteToLinear(c & 0xFF);
				}
			}
			pmb.release();

			int r = radius << 1;
			r *= r;
			data[offset] = (byte) (ColorUtil.linearTosRGB(linearR / r) * 255);
			data[offset + 1] = (byte) (ColorUtil.linearTosRGB(linearG / r) * 255);
			data[offset + 2] = (byte) (ColorUtil.linearTosRGB(linearB / r) * 255);
		}

		return ((data[offset++] & 0xFF) << 16) | ((data[offset++] & 0xFF) << 8) | (data[offset] & 0xFF);
	}

	private static boolean isBlended(byte[] data, int pos) {
		return (data[pos++] | data[pos++] | data[pos]) != 0;
	}

	// endregion

	private static AsyncCullCheck cc;

	static final class AsyncCullCheck extends Thread {
		Vec3d a0 = a.scale(1), a1 = a.scale(1);
		Entity viewer;

		SimpleList<Entity> task = new SimpleList<>();
		Int2IntMap done = new Int2IntMap();

		volatile int state;

		AsyncCullCheck() {
			setName("Async Entity Culler");
			setDaemon(true);
		}

		@Override
		public void run() {
			// todo
			while (ClientProxy.mc.world != null) {
				done.clear();

				Entity viewer1 = viewer;
				Reflection.setVec(a0, viewer1.posX, viewer1.posY + viewer1.getEyeHeight(), viewer1.posZ);

				for (int i = 0; i < task.size(); i++) {
					Entity entity = task.get(i);
					byte result = 0;

					Reflection.setVec(a1, entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);
					if (!rayTraceOpaque(viewer1.world, a0, a1)) {result = 1;} else {
						Reflection.setVec(a1, entity.posX, entity.posY, entity.posZ);
						if (!rayTraceOpaque(viewer1.world, a0, a1)) result = 1;
					}
					done.putInt(entity.getEntityId(), result);
				}
				task.clear();

				state = 0;
				while (state == 0) LockSupport.park();
			}
			cc = null;
		}

		public boolean checkCull(Entity entity, Entity viewer) {
			this.viewer = viewer;

			int v = done.getOrDefaultInt(entity.getEntityId(), -1);
			if (v < 0) {
				task.add(entity);
				v = 0;
			}

			return v != 0;
		}

		public void reset() {
			task.clear();
		}
	}

	public static void resetEntityCuller() {
		if (cc != null) cc.reset();
	}

	public static boolean culledBy(Entity entity, Entity viewer) {
		ICamera camera = ((CameraAccess) Minecraft.getMinecraft().renderGlobal).getCamera();
		if (camera == null) return false;

		if (camera.isBoundingBoxInFrustum(entity.getRenderBoundingBox())) {
			if (cc != null) {
				return cc.checkCull(entity, viewer);
			}

			if (entity.getDistanceSq(viewer) < 100) return false;

			Reflection.setVec(a, viewer.posX, viewer.posY + viewer.getEyeHeight(), viewer.posZ);
			Reflection.setVec(b, entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);
			if (!rayTraceOpaque(viewer.world, a, b)) return false;

			Reflection.setVec(b, entity.posX, entity.posY, entity.posZ);
			return rayTraceOpaque(viewer.world, a, b);
		}

		return true;
	}

	public static boolean culledBy(Entity rve, TileEntity tile) {
		return false;
	}

	public static boolean rayTraceOpaque(World world, Vec3d begin, Vec3d end) {
		if (!Double.isNaN(begin.x) && !Double.isNaN(begin.y) && !Double.isNaN(begin.z)) {
			if (!Double.isNaN(end.x) && !Double.isNaN(end.y) && !Double.isNaN(end.z)) {
				BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();

				int i = MathHelper.floor(end.x);
				int j = MathHelper.floor(end.y);
				int k = MathHelper.floor(end.z);
				int x = MathHelper.floor(begin.x);
				int y = MathHelper.floor(begin.y);
				int z = MathHelper.floor(begin.z);
				pos.setPos(x, y, z);
				IBlockState state = world.getBlockState(pos);
				Block block = state.getBlock();
				if (state.getCollisionBoundingBox(world, pos) != Block.NULL_AABB && block.canCollideCheck(state, false) && (state.isFullCube() || state.isOpaqueCube())) {
					RayTraceResult r = state.collisionRayTrace(world, pos, begin, end);
					if (r != null) {
						pos.release();
						return true;
					}
				}

				int maxDist1 = 200;

				Reflection.setVec(c, begin.x, begin.y, begin.z);
				begin = c;
				while (maxDist1-- >= 0) {
					if (Double.isNaN(begin.x) || Double.isNaN(begin.y) || Double.isNaN(begin.z)) {
						return false;
					}

					if (x == i && y == j && z == k) {
						return false;
					}

					boolean flag2 = true;
					boolean flag = true;
					boolean flag1 = true;
					double d0 = 999.0D;
					double d1 = 999.0D;
					double d2 = 999.0D;
					if (i > x) {
						d0 = x + 1.0D;
					} else if (i < x) {
						d0 = x + 0.0D;
					} else {
						flag2 = false;
					}

					if (j > y) {
						d1 = y + 1.0D;
					} else if (j < y) {
						d1 = y + 0.0D;
					} else {
						flag = false;
					}

					if (k > z) {
						d2 = z + 1.0D;
					} else if (k < z) {
						d2 = z + 0.0D;
					} else {
						flag1 = false;
					}

					double d3 = 999.0D;
					double d4 = 999.0D;
					double d5 = 999.0D;
					double d6 = end.x - begin.x;
					double d7 = end.y - begin.y;
					double d8 = end.z - begin.z;
					if (flag2) {
						d3 = (d0 - begin.x) / d6;
					}

					if (flag) {
						d4 = (d1 - begin.y) / d7;
					}

					if (flag1) {
						d5 = (d2 - begin.z) / d8;
					}

					if (d3 == -0.0D) {
						d3 = -1.0E-4D;
					}

					if (d4 == -0.0D) {
						d4 = -1.0E-4D;
					}

					if (d5 == -0.0D) {
						d5 = -1.0E-4D;
					}

					EnumFacing face;
					if (d3 < d4 && d3 < d5) {
						face = i > x ? EnumFacing.WEST : EnumFacing.EAST;
						Reflection.setVec(begin, d0, begin.y + d7 * d3, begin.z + d8 * d3);
					} else if (d4 < d5) {
						face = j > y ? EnumFacing.DOWN : EnumFacing.UP;
						Reflection.setVec(begin, begin.x + d6 * d4, d1, begin.z + d8 * d4);
					} else {
						face = k > z ? EnumFacing.NORTH : EnumFacing.SOUTH;
						Reflection.setVec(begin, begin.x + d6 * d5, begin.y + d7 * d5, d2);
					}

					x = MathHelper.floor(begin.x) - (face == EnumFacing.EAST ? 1 : 0);
					y = MathHelper.floor(begin.y) - (face == EnumFacing.UP ? 1 : 0);
					z = MathHelper.floor(begin.z) - (face == EnumFacing.SOUTH ? 1 : 0);
					pos.setPos(x, y, z);
					state = world.getBlockState(pos);
					block = state.getBlock();
					if (state.getMaterial() == Material.PORTAL || state.getCollisionBoundingBox(world, pos) != Block.NULL_AABB && (state.isFullCube() || state.isOpaqueCube())) {
						if (block.canCollideCheck(state, false)) {
							RayTraceResult r = state.collisionRayTrace(world, pos, begin, end);
							if (r != null) {
								pos.release();
								return true;
							}
						}
					}
				}

				pos.release();
			}
		}
		return false;
	}

	public static void rpl_renderNameTag(float partialTicks, RenderManager rm, Entity entity) {
		double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - rm.renderPosX;
		double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - rm.renderPosY;
		double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - rm.renderPosZ;

		if (entity.hasCustomName()) {
			Render<Entity> re = rm.getEntityRenderObject(entity);
			if (re != null && rm.renderEngine != null) {
				try {
					ReflectionClient.HELPER.renderName(re, entity, x, y, z);
				} catch (Throwable e) {
					throw new ReportedException(CrashReport.makeCrashReport(e, "Rendering entity in world"));
				}
			}
		}
	}

	private static FieldAccessor biomeField, colorField;

	static {
		try {
			biomeField = ReflectionUtils.access(ReflectionUtils.getField(BiomeEvent.class, "biome"));
			colorField = ReflectionUtils.access(ReflectionUtils.getField(BiomeEvent.BiomeColor.class, "originalColor"));
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public void setEvent(BiomeEvent.BiomeColor e, Biome b, int color) {
		biomeField.setObject(e, b);
		colorField.setInt(e, color);
		e.setNewColor(color);
	}
}
