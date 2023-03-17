package ilib.misc;

import ilib.util.BlockHelper;
import ilib.util.BlockWalker;
import ilib.util.Reflection;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.util.Helpers;

import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;

/**
 * todo Maybe cache?
 *
 * @author solo6975
 * @since 2022/4/6 0:06
 */
public class RedstoneUpdater extends BlockWalker implements Consumer<RedstoneUpdater.RedstoneInfo> {
	static final class RedstoneInfo extends BlockPos.MutableBlockPos {
		int redstone;

		public RedstoneInfo() {}
	}

	private final SimpleList<RedstoneInfo> pool;
	private final ToIntMap<RedstoneInfo> powers;
	private final EnergyMapper walker;

	BlockPos begin;
	public World world;

	public RedstoneUpdater() {
		this.powers = new ToIntMap<>();
		this.walker = new EnergyMapper();
		this.pool = new SimpleList<>();
	}

	@Override
	public boolean walk(BlockPos start) {
		if (world.isAirBlock(start)) return false;

		this.begin = start;
		this.powers.clear();

		Reflection.HELPER.setRedstoneProvidePower(Blocks.REDSTONE_WIRE, false);
		try {
			boolean endNormally = super.walk(start);
			Reflection.HELPER.setRedstoneProvidePower(Blocks.REDSTONE_WIRE, true);

			if (endNormally) update();
			return endNormally;
		} catch (Throwable e) {
			Reflection.HELPER.setRedstoneProvidePower(Blocks.REDSTONE_WIRE, true);
			e.printStackTrace();
			return false;
		}
	}

	public void update() {
		if (closed.isEmpty()) return;

		IBlockState redDefault = Blocks.REDSTONE_WIRE.getDefaultState();
		World w = this.world;

		constructEnergyMap();

		BlockPos.PooledMutableBlockPos tmp = BlockPos.PooledMutableBlockPos.retain();

		Iterator<RedstoneInfo> itr = Helpers.cast(closed.iterator());
		while (itr.hasNext()) {
			RedstoneInfo pos = itr.next();

			Chunk c = w.getChunk(pos);
			ExtendedBlockStorage arr = c.getBlockStorageArray()[pos.getY() >> 4];

			IBlockState original = arr.get(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
			IBlockState state = redDefault.withProperty(BlockRedstoneWire.POWER, pos.redstone);
			arr.set(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state);

			if (original != state) {
				w.notifyBlockUpdate(pos, original, state, BlockHelper.PLACEBLOCK_UPDATE | BlockHelper.PLACEBLOCK_SENDCHANGE);
				w.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
				for (EnumFacing facing : EnumFacing.VALUES) {
					w.notifyNeighborsOfStateExcept(tmp.setPos(pos).move(facing), state.getBlock(), facing.getOpposite());
				}
			}

			release(pos);
		}

		tmp.release();
	}

	private void constructEnergyMap() {
		EnergyMapper w = walker;
		for (ToIntMap.Entry<RedstoneInfo> entry : powers.selfEntrySet()) {
			w.red = entry.v;
			w.walk(entry.k);

			SimpleList<MyHashSet<RedstoneInfo>> lv = w.byLevel;
			int max = entry.v - w.red - 1;
			for (int i = 0; i < max; i++) {
				this.level = entry.v - i;
				MyHashSet<RedstoneInfo> poss = lv.get(i);
				poss.forEach(this);
			}

			release(entry.k);
		}
	}

	private int level;

	@Override
	public void accept(RedstoneInfo pos) {
		if (pos.redstone < level) {
			pos.redstone = level;
		}
	}

	@Override
	public boolean canWalk(int cycle) {
		// magic (当只有一个红石时)
		if (cycle == 0) closed.add(alloc(begin));
		return cycle < 128 && closed.size() < 4096;
	}

	protected void addNear(Set<BlockPos> list, BlockPos pos) {
		BlockPos.PooledMutableBlockPos tmp = BlockPos.PooledMutableBlockPos.retain();
		for (EnumFacing facing : EnumFacing.VALUES) {
			if (isValidPos(tmp.setPos(pos).move(facing), facing)) {
				list.add(alloc(tmp));
			}
		}

		World w = this.world;

		// 上传
		if (!w.getBlockState(tmp.setPos(pos).move(EnumFacing.UP)).isSideSolid(w, tmp, EnumFacing.DOWN)) {
			RedstoneInfo tmp2 = alloc(tmp);
			for (EnumFacing facing : EnumFacing.HORIZONTALS) {
				if (isValidPos(tmp.setPos(tmp2).move(facing), null)) {
					list.add(alloc(tmp));
				}
			}
			release(tmp2);
		}

		// 下传
		for (EnumFacing facing : EnumFacing.HORIZONTALS) {
			IBlockState state = w.getBlockState(tmp.setPos(pos).move(facing));
			if (!state.isSideSolid(w, tmp, facing)) {
				// 石台阶
				if (isValidPos(tmp.move(EnumFacing.DOWN), null)) {
					list.add(alloc(tmp));
				}
			}
		}

		tmp.release();
	}

	@Override
	public boolean isValidPos(BlockPos pos, EnumFacing from) {
		World w = this.world;

		if (!w.isBlockLoaded(pos, false)) return false;

		IBlockState state = w.getBlockState(pos);
		if (state.getBlock() instanceof BlockRedstoneWire) {
			return true;
		}

		if (state.getMaterial() == Material.AIR || from == null) return false;

		int red = w.getRedstonePower(pos, from);
		if (red > 0) {
			powers.putInt(alloc(pos), red);
		}
		return false;
	}

	// region Utilities

	private RedstoneInfo alloc(BlockPos pos) {
		SimpleList<RedstoneInfo> P = this.pool;
		return (RedstoneInfo) (pos instanceof RedstoneInfo ? pos : (P.isEmpty() ? new RedstoneInfo() : P.remove(P.size() - 1)).setPos(pos));
	}

	private void release(RedstoneInfo info) {
		if (pool.size() < 100) {
			info.redstone = 0;
			pool.add(info);
		}
	}

	@Override
	protected void release0(BlockPos p) {
		release((RedstoneInfo) p);
	}

	// endregion

	private class EnergyMapper extends BlockWalker {
		SimpleList<MyHashSet<RedstoneInfo>> byLevel = new SimpleList<>();
		int red;

		protected void addNear(Set<BlockPos> list, BlockPos pos) {
			BlockPos.PooledMutableBlockPos tmp = BlockPos.PooledMutableBlockPos.retain();
			MyHashSet<BlockPos> traveled = RedstoneUpdater.this.closed;

			World w = world;
			if (!this.closed.isEmpty()) {
				for (EnumFacing facing : EnumFacing.HORIZONTALS) {
					BlockPos pos1 = traveled.find(tmp.setPos(pos).move(facing));
					if (pos1 != tmp) list.add(pos1);

					pos1 = traveled.find(tmp.move(EnumFacing.UP));
					if (pos1 != tmp) list.add(pos1);

					if (w.getBlockState(tmp.setPos(pos).move(EnumFacing.DOWN)).isSideSolid(w, tmp, facing)) {
						pos1 = traveled.find(tmp.move(facing));
						if (pos1 != tmp) list.add(pos1);
					}
				}
			} else {
				for (EnumFacing facing : EnumFacing.VALUES) {
					BlockPos pos1 = traveled.find(tmp.setPos(pos).move(facing));
					if (pos1 != tmp && w.getRedstonePower(pos, facing.getOpposite()) > 0) {
						list.add(pos1);
					}
				}
			}

			tmp.release();
		}

		@Override
		@SuppressWarnings("unchecked")
		public boolean canWalk(int cycle) {
			if (cycle > 0) {
				SimpleList<MyHashSet<RedstoneInfo>> L = byLevel;
				MyHashSet<RedstoneInfo> set;
				if (L.size() < cycle) {
					L.add(set = new MyHashSet<>());
				} else {
					set = L.get(cycle - 1);
					set.clear();
				}
				set.addAll((Collection<RedstoneInfo>) (Object) closed);

				if (cycle > 1) {
					set.removeAll(L.get(cycle - 2));
				}
			}
			return red-- > 0;
		}

		@Override
		public boolean isValidPos(BlockPos pos, EnumFacing from) {
			return true;
		}
	}
}
