package ilib.util;

import com.google.common.collect.UnmodifiableIterator;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ObjectIntIdentityMap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.PooledMutableBlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import net.minecraftforge.registries.GameData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2021/6/1 1:51
 */
public final class BlockHelper {
	public static final int PLACEBLOCK_NOTHING = 0;
	public static final int PLACEBLOCK_UPDATE = 1;
	public static final int PLACEBLOCK_SENDCHANGE = 2;
	public static final int PLACEBLOCK_NO_RERENDER = 4;
	public static final int PLACEBLOCK_RENDERMAIN = 8;
	public static final int PLACEBLOCK_NO_OBSERVER = 16;

	public static final IBlockState AIR_STATE = Blocks.AIR.getDefaultState();
	public static final ObjectIntIdentityMap<IBlockState> S2I = GameData.getBlockStateIDMap();

	public static void notifyWall(World w, BlockPos v0, BlockPos v1) {
		BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain(0, 0, 0);

		// 0 0 0 to 1 0 1   xOz plane D
		// 0 0 0 to 1 1 0   xOy plane D
		// 0 0 0 to 0 1 1   yOz plane D

		// 1 1 1 to 1 0 1   xOz plane U
		// 1 1 1 to 1 1 0   xOy plane U
		// 1 1 1 to 0 1 1   yOz plane U

		notify0(w, v0, pos1.setPos(v1.getX(), v0.getY(), v1.getZ()));
		notify0(w, v0, pos1.setPos(v1.getX(), v1.getY(), v0.getZ()));
		notify0(w, v0, pos1.setPos(v0.getX(), v1.getY(), v1.getZ()));

		notify0(w, pos1.setPos(v1.getX(), v0.getY(), v1.getZ()), v1);
		notify0(w, pos1.setPos(v1.getX(), v1.getY(), v0.getZ()), v1);
		notify0(w, pos1.setPos(v0.getX(), v1.getY(), v1.getZ()), v1);

		pos1.release();
	}

	private static void notify0(World world, BlockPos str, BlockPos end) {
		int endX = end.getX();
		int endY = end.getY();
		int endZ = end.getZ();

		BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(0, 0, 0);

		for (int i = str.getX(); i <= endX; i++) {
			for (int j = str.getY(); j <= endY; j++) {
				for (int k = str.getZ(); k <= endZ; k++) {
					world.scheduleUpdate(pos.setPos(i, j, k), world.getBlockState(pos).getBlock(), 0);
				}
			}
		}

		pos.release();
	}

	public static void fillWall(World w, IBlockState state, BlockPos v0, BlockPos v1, int flag) {
		BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain(0, 0, 0);

		// 0 0 0 to 1 0 1   xOz plane D
		// 0 0 0 to 1 1 0   xOy plane D
		// 0 0 0 to 0 1 1   yOz plane D

		// 1 1 1 to 1 0 1   xOz plane U
		// 1 1 1 to 1 1 0   xOy plane U
		// 1 1 1 to 0 1 1   yOz plane U

		fillBlock0(w, state, v0, pos1.setPos(v1.getX(), v0.getY(), v1.getZ()), flag);
		fillBlock0(w, state, v0, pos1.setPos(v1.getX(), v1.getY(), v0.getZ()), flag);
		fillBlock0(w, state, v0, pos1.setPos(v0.getX(), v1.getY(), v1.getZ()), flag);

		fillBlock0(w, state, pos1.setPos(v1.getX(), v0.getY(), v1.getZ()), v1, flag);
		fillBlock0(w, state, pos1.setPos(v1.getX(), v1.getY(), v0.getZ()), v1, flag);
		fillBlock0(w, state, pos1.setPos(v0.getX(), v1.getY(), v1.getZ()), v1, flag);

		pos1.release();
	}

	public static void fillVertex(World w, IBlockState state, BlockPos v0, BlockPos v1, int flag) {
		BlockPos.PooledMutableBlockPos pos0 = BlockPos.PooledMutableBlockPos.retain(0, 0, 0);
		BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain(0, 0, 0);

		// 0 0 0 to 0 1 0, Y-Axis
		fillBlock0(w, state, v0, pos1.setPos(v0.getX(), v1.getY(), v0.getZ()), flag);
		// 1 0 0 to 1 1 0
		fillBlock0(w, state, pos0.setPos(v1.getX(), v0.getY(), v0.getZ()), pos1.setPos(v1.getX(), v1.getY(), v0.getZ()), flag);
		// 0 0 1 to 0 1 1
		fillBlock0(w, state, pos0.setPos(v0.getX(), v0.getY(), v1.getZ()), pos1.setPos(v0.getX(), v1.getY(), v1.getZ()), flag);
		// 1 0 1 to 1 1 1
		fillBlock0(w, state, pos0.setPos(v1.getX(), v0.getY(), v1.getZ()), v1, flag);

		// 0 0 0 to 1 0 0, X-Axis
		fillBlock0(w, state, v0, pos1.setPos(v1.getX(), v0.getY(), v0.getZ()), flag);
		// 0 0 1 to 1 0 1
		fillBlock0(w, state, pos0.setPos(v0.getX(), v0.getY(), v1.getZ()), pos1.setPos(v1.getX(), v0.getY(), v1.getZ()), flag);
		// 0 1 0 to 1 1 0
		fillBlock0(w, state, pos0.setPos(v0.getX(), v1.getY(), v0.getZ()), pos1.setPos(v1.getX(), v1.getY(), v0.getZ()), flag);
		// 0 1 1 to 1 1 1
		fillBlock0(w, state, pos0.setPos(v0.getX(), v1.getY(), v1.getZ()), v1, flag);

		// 0 0 0 to 0 0 1, Z-Axis
		fillBlock0(w, state, v0, pos1.setPos(v0.getX(), v0.getY(), v1.getZ()), flag);
		// 1 0 0 to 1 0 1
		fillBlock0(w, state, pos0.setPos(v1.getX(), v0.getY(), v0.getZ()), pos1.setPos(v1.getX(), v0.getY(), v1.getZ()), flag);
		// 1 1 0 to 1 1 1
		fillBlock0(w, state, pos0.setPos(v1.getX(), v1.getY(), v0.getZ()), v1, flag);
		// 0 1 0 to 0 1 1
		fillBlock0(w, state, pos0.setPos(v0.getX(), v1.getY(), v0.getZ()), pos1.setPos(v0.getX(), v1.getY(), v1.getZ()), flag);

		pos0.release();
		pos1.release();
	}

	@Deprecated
	public static class MutableBlockPos extends BlockPos.MutableBlockPos {
		public MutableBlockPos() {
			this(0, 0, 0);
		}

		public MutableBlockPos(BlockPos pos) {
			super(pos.getX(), pos.getY(), pos.getZ());
		}

		public MutableBlockPos(int x, int y, int z) {
			super(x, y, z);
		}

		public MutableBlockPos add1(Vec3i vec3i) {
			this.x += vec3i.getX();
			this.y += vec3i.getY();
			this.z += vec3i.getZ();
			return this;
		}

		public MutableBlockPos add1(int x, int y, int z) {
			this.x += x;
			this.y += y;
			this.z += z;
			return this;
		}

		public MutableBlockPos add1(double x, double y, double z) {
			return add1((int) x, (int) y, (int) z);
		}
	}

	/**
	 * 画直线(近似)
	 *
	 * @param x1 x起始
	 * @param y1 y起始
	 * @param z1 z起始
	 * @param x2 x结束
	 * @param y2 y结束
	 * @param z2 z结束
	 *
	 * @return 点集Iterator
	 */
	public static Iterable<BlockPos> bresenham(double x1, double y1, double z1, double x2, double y2, double z2) {
		return () -> new BresenhamLine(x1, y1, z1, x2, y2, z2);
	}

	public static final class BresenhamLine implements Iterator<BlockPos> {
		public double x, y, z;
		public final double stepX, stepY, stepZ;
		public final int len;
		int i;

		final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		public BresenhamLine(double x1, double y1, double z1, double x2, double y2, double z2) {
			x = x1;
			y = y1;
			z = z1;

			double dx = x2 - x1;
			double dy = y2 - y1;
			double dz = z2 - z1;

			len = (int) Math.ceil(Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))));

			stepX = dx / len;
			stepY = dy / len;
			stepZ = dz / len;
		}

		@Override
		public boolean hasNext() {
			return i < len * 3;
		}

		@Override
		public BlockPos next() {
			int x1 = pos.getX();
			int y1 = pos.getY();
			int z1 = pos.getZ();

			do {
				pos.setPos(x, y, z);
				switch (i++ % 3) {
					case 0:
						x += stepX;
						break;
					case 1:
						y += stepY;
						break;
					case 2:
						z += stepZ;
						break;
				}
			} while (x1 == pos.getX() && y1 == pos.getY() && z1 == pos.getZ());
			return pos;
		}
	}

	/**
	 * 最大固体方块高度
	 */
	public static int getSolidBlockY(World world, int x, int z) {
		BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(x, 0, z);
		Chunk c = world.getChunk(pos);
		int y = c.getTopFilledSegment() + 16;

		IBlockState state;
		do {
			if (--y < 0) break;
			state = c.getBlockState(pos.setPos(x, y, z));
		} while (!state.isSideSolid(world, pos, EnumFacing.UP));
		pos.release();
		return y;
	}

	/**
	 * 最大可替换方块高度
	 */
	public static int getSurfaceBlockY(World world, int x, int z) {
		BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(x, 0, z);
		Chunk c = world.getChunk(pos);
		int y = c.getTopFilledSegment() + 16;

		IBlockState state;
		Block block;
		do {
			if (--y < 0) break;

			state = c.getBlockState(pos.setPos(x, y, z));
			block = state.getBlock();
		} while (block.isAir(state, world, pos) || block.isReplaceable(world, pos) || block.isLeaves(state, world, pos) || block.isFoliage(world, pos) || block.canBeReplacedByLeaves(state, world,
																																													  pos));
		pos.release();
		return y;
	}

	/**
	 * 最大块高度
	 */
	public static int getTopBlockY(World world, int x, int z) {
		BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(x, 0, z);
		Chunk c = world.getChunk(pos);
		int y = c.getTopFilledSegment() + 16;

		IBlockState state;
		do {
			if (--y < 0) break;
			pos.setY(y);

			state = c.getBlockState(pos);
		} while (state.getBlock().isAir(state, world, pos));
		pos.release();
		return y;
	}

	/**
	 * 获取非空气方块
	 */
	public static List<BlockPos> getBlockAround(int size, EnumFacing facing, BlockPos pos, World world) {

		BlockPos.PooledMutableBlockPos pos1;
		BlockPos.PooledMutableBlockPos pos2;
		List<BlockPos> actualList = new ArrayList<>();

		if (facing.getAxis().isHorizontal()) {
			if (facing == EnumFacing.NORTH || facing == EnumFacing.SOUTH) {
				pos1 = BlockPos.PooledMutableBlockPos.retain(pos.getX() + size, pos.getY() + size, pos.getZ());
				pos2 = BlockPos.PooledMutableBlockPos.retain(pos.getX() - size, pos.getY() - size, pos.getZ());
			} else {
				pos1 = BlockPos.PooledMutableBlockPos.retain(pos.getX(), pos.getY() + size, pos.getZ() + size);
				pos2 = BlockPos.PooledMutableBlockPos.retain(pos.getX(), pos.getY() - size, pos.getZ() - size);
			}

			while (pos2.getY() < pos.getY() - 1) {
				pos1.offset(EnumFacing.UP);
				pos2.offset(EnumFacing.UP);
			}
		} else {
			pos1 = BlockPos.PooledMutableBlockPos.retain(pos.getX() + size, pos.getY(), pos.getZ() + size);
			pos2 = BlockPos.PooledMutableBlockPos.retain(pos.getX() - size, pos.getY(), pos.getZ() - size);
		}

		for (BlockPos.MutableBlockPos blockPos : BlockPos.getAllInBoxMutable(pos1, pos2)) {
			if (!world.isAirBlock(blockPos)) actualList.add(blockPos.toImmutable());
		}

		pos1.release();
		pos2.release();

		return actualList;
	}

	public static int getBlockCountAround(int size, BlockPos pos, World world, Block block) {
		PooledMutableBlockPos pos1 = PooledMutableBlockPos.retain(pos).move(EnumFacing.UP, size).move(EnumFacing.NORTH, size).move(EnumFacing.WEST, size);
		PooledMutableBlockPos pos2 = PooledMutableBlockPos.retain(pos).move(EnumFacing.DOWN, size).move(EnumFacing.SOUTH, size).move(EnumFacing.EAST, size);

		int i = 0;
		for (BlockPos p : BlockPos.getAllInBoxMutable(pos1, pos2)) {
			if (world.getBlockState(p).getBlock() == block) i++;
		}

		pos1.release();
		pos2.release();

		return i;
	}

	public static void fillBlockIfLoaded(World world, IBlockState state, BlockPos str, BlockPos end) {
		fillBlock0(world, state, str, end, PLACEBLOCK_NO_OBSERVER | PLACEBLOCK_SENDCHANGE);
	}

	public static void fillBlock(World world, IBlockState state, BlockPos str, BlockPos end) {
		fillBlock0(world, state, str, end, PLACEBLOCK_SENDCHANGE);
	}

	public static void fillBlock0(World world, IBlockState state, BlockPos str, BlockPos end, int type) {
		int endX = end.getX();
		int endY = end.getY();
		int endZ = end.getZ();

		BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(0, 0, 0);

		for (int i = str.getX(); i <= endX; i++) {
			for (int j = str.getY(); j <= endY; j++) {
				for (int k = str.getZ(); k <= endZ; k++) {
					world.setBlockState(pos.setPos(i, j, k), state, type);
				}
			}
		}
		pos.release();
	}

	/* BLOCK UPDATES */
	public static void callBlockUpdate(World world, BlockPos pos) {
		IBlockState state = world.getBlockState(pos);
		world.notifyBlockUpdate(pos, state, state, PLACEBLOCK_UPDATE | PLACEBLOCK_SENDCHANGE);
	}

	public static void sendTileUpdate(@Nonnull TileEntity tile) {
		World world = tile.getWorld();
		BlockPos pos = tile.getPos();
		if (!world.isRemote) {
			tile.markDirty();
		} else {
			world.markBlockRangeForRenderUpdate(pos, pos);
		}
	}

	public static void updateBlock(World world, BlockPos pos) {
		if (!world.isRemote) {
			((WorldServer) world).getPlayerChunkMap().markBlockForUpdate(pos);
		} else {
			world.markBlockRangeForRenderUpdate(pos, pos);
		}
	}

	public static ItemStack toStack(IBlockState input) {
		return new ItemStack(input.getBlock(), 1, input.getBlock().damageDropped(input));
	}

	public void callNeighborStateChange(World world, BlockPos pos) {
		world.notifyNeighborsOfStateChange(pos, world.getBlockState(pos).getBlock(), false);
	}

	public void callNeighborTileChange(World world, BlockPos pos) {
		world.updateComparatorOutputLevel(pos, world.getBlockState(pos).getBlock());
	}

	/**
	 * minecraft:stone@0
	 * minecraft:stone@varient=233
	 */
	public static IBlockState stateFromText(String id) {
		int i = id.indexOf('@');
		String blockId = i == -1 ? id : id.substring(0, i);

		ResourceLocation loc = new ResourceLocation(blockId);
		if (!Block.REGISTRY.containsKey(loc)) {
			throw new IllegalArgumentException("Block " + blockId + " not found ");
		} else {
			Block block = Block.REGISTRY.getObject(loc);
			return i == -1 ? block.getDefaultState() : matchState(block, id.substring(i + 1));
		}
	}

	public static String stateToText(IBlockState state) {
		CharList tmp = IOUtil.getSharedCharBuf();
		return tmp.append(state.getBlock().getRegistryName().toString()).append('@').append(state.getBlock().getMetaFromState(state)).toString();
	}

	public static String stateToTextEx(IBlockState state) {
		CharList tmp = IOUtil.getSharedCharBuf();
		tmp.append(state.getBlock().getRegistryName().toString()).append('@');

		UnmodifiableIterator<Map.Entry<IProperty<?>, Comparable<?>>> itr = state.getProperties().entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<IProperty<?>, Comparable<?>> entry = itr.next();
			tmp.append(entry.getKey().getName()).append('=').append(entry.getValue().toString());
			if (!itr.hasNext()) break;
			tmp.append(',');
		}

		return tmp.toString();
	}


	@SuppressWarnings({"rawtypes", "unchecked", "deprecation"})
	public static IBlockState matchState(Block block, String desc) {
		if (TextUtil.isNumber(desc) == 0) {
			int meta = Integer.parseInt(desc);
			if (meta < 0) {
				throw new IllegalArgumentException("meta < 0 : " + desc);
			} else {
				return block.getStateFromMeta(meta);
			}
		} else {
			IBlockState state = block.getDefaultState();
			if (!"default".equals(desc)) {
				BlockStateContainer container = block.getBlockState();
				SimpleList<String> tmp = new SimpleList<>();

				List<String> states = TextUtil.split(desc, ',');
				for (int i = 0; i < states.size(); i++) {
					tmp.clear();
					TextUtil.split(tmp, states.get(i), '=', 2);
					if (tmp.size() < 2) return null;

					IProperty prop = container.getProperty(tmp.get(0));
					if (prop == null) {
						throw new IllegalArgumentException("Property " + tmp.get(0) + " not found ");
					}

					Comparable val = (Comparable) prop.parseValue(tmp.get(1)).orNull();
					if (val == null) {
						throw new IllegalArgumentException("Value " + tmp.get(1) + " for property " + tmp.get(0) + " not found ");
					}

					state = state.withProperty(prop, val);
				}
			}
			return state;
		}
	}
}