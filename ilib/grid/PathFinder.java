package ilib.grid;

import ilib.util.BlockWalker;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.IBlockAccess;

import java.util.Set;

/**
 * @author Roj233
 * @since 2022/5/13 16:18
 */
class PathFinder extends BlockWalker {
	GridManager owner;
	IBlockAccess world;
	SimpleList<GridNode> pool = new SimpleList<>();
	MyHashSet<GridNode> equipments = new MyHashSet<>();

	public PathFinder(GridManager manager) {
		owner = manager;
	}

	@Override
	protected void reset(BlockPos start) {
		if (!equipments.isEmpty()) {
			for (GridNode node : equipments)
				release(node);
			equipments.clear();
		}

		GridEntry entry = owner.getGrid(world.getTileEntity(start), false);

		GridNode node = alloc(start);
		node.entry = entry;
		if (!(entry instanceof IConductor)) {
			equipments.add(node);
		} else {
			closed.add(node);
		}
	}

	@Override
	protected void addNear(Set<BlockPos> list, BlockPos pos) {
		IBlockAccess w = world;
		BlockPos.MutableBlockPos tmp = this.tmp;

		for (EnumFacing facing : EnumFacing.VALUES) {
			if (isValidPos(tmp.setPos(pos).move(facing), facing)) {
				GridNode node = alloc(tmp);
				node.entry = owner.getGrid(w.getTileEntity(node), false);
				list.add(node);
			}
		}
	}

	@Override
	protected boolean canWalk(int cycle) {
		if (cycle > 10) pause();
		return closed.size() < 8000;
	}

	@Override
	protected boolean shouldPause(MyHashSet<BlockPos> curr, MyHashSet<BlockPos> next) {
		return next.size() > 200;
	}

	@Override
	protected boolean isValidPos(BlockPos pos, EnumFacing from) {
		IBlockAccess w = world;

		TileEntity tile = w.getTileEntity(pos);
		if (tile == null) return false;
		GridEntry entry = owner.getGrid(tile, false);
		if (entry == null) return false;

		if (entry instanceof IConductor) return true;

		GridNode node = alloc(pos);
		node.entry = entry;
		node.side = from.getOpposite();
		equipments.add(node);
		return false;
	}

	private GridNode alloc(BlockPos pos) {
		SimpleList<GridNode> P = this.pool;
		return (P.isEmpty() ? new GridNode() : P.remove(P.size() - 1)).setPos(pos);
	}

	private void release(GridNode info) {
		if (pool.size() < 1024) pool.add(info);
	}

	@Override
	protected void release0(BlockPos p) {
		release((GridNode) p);
	}

	static final class GridNode extends BlockPos.MutableBlockPos {
		GridEntry entry;
		EnumFacing side;

		@Override
		public GridNode setPos(Vec3i pos) {
			super.setPos(pos);
			entry = null;
			side = null;
			return this;
		}

		@Override
		public boolean equals(Object o) {
			if (!super.equals(o)) return false;
			return side == ((GridNode) o).side;
		}

		@Override
		public int hashCode() {
			int h = super.hashCode();
			if (side != null) h = 31 * h + side.hashCode();
			return h;
		}
	}
}
