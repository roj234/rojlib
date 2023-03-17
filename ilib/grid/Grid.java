package ilib.grid;

import ilib.ImpLib;
import ilib.client.renderer.WaypointRenderer;
import ilib.grid.CircuitGraph.Path;
import ilib.math.PositionProvider;
import ilib.math.Section;
import ilib.util.DimensionHelper;
import roj.collect.AbstractIterator;
import roj.collect.IntIterator;
import roj.collect.IntSet;
import roj.collect.MyHashSet;
import roj.util.Helpers;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/5/13 16:12
 */
public class Grid implements PositionProvider {
	int id;
	private final GridManager owner;

	int world;
	private final Section box;

	private final BlockPos.MutableBlockPos tmp = new BlockPos.MutableBlockPos();

	final GetByPos objects;
	final AbstractIterator<GridEntry> objItr;
	private int conductors;
	private List<Path> generators;

	private final CircuitGraph graph;
	private final Path[] tmp2 = new Path[6];
	private boolean routeDirty, valueDirty;

	private PathFinder task;
	private boolean merging;

	public static void explode(World world, BlockPos pos, int level) {
		world.newExplosion(null, pos.getX(), pos.getY(), pos.getZ(), level, true, true);
	}

	public Grid(GridManager man, World w) {
		owner = man;

		world = DimensionHelper.idFor(w);
		box = new Section();

		objects = new GetByPos();
		objItr = objects.setItr();

		graph = new CircuitGraph();
	}

	public boolean addNode(GridEntry entry) {
		if (conductors > 0) {
			GridEntry node = objects.getByPos(entry.pos());
			if (null != node) {
				if (entry == node) return true;
				ImpLib.logger().warn("重复注册节点 {}, 位置 {}", entry, entry.pos());
			}

			if (!merging && !hasNearbyConductor(entry.pos())) return false;
		}

		entry.enterGrid(this);
		objects.add(entry);
		routeDirty = true;

		if (entry instanceof IConductor) {
			conductors++;
		}
		if (entry.canProvidePower()) {
			graph.addSource(entry);
		} else {
			graph.addNode(entry);
		}

		if (merging) return true;

		if (objects.isEmpty()) {
			box.set(entry.pos(), entry.pos());
		} else {
			AbstractIterator<GridEntry> it = objItr;
			for (it.reset(); it.hasNext(); ) {
				it.next().gridUpdate();
			}

			if (box.contains(entry.pos())) return true;
			box.expandIf(entry.pos());
		}
		owner.updatePosition(this);

		return true;
	}

	public boolean removeNode(GridEntry entry) {
		if (!objects.remove(entry)) {
			return false;
		}
		routeDirty = true;

		if (entry instanceof IConductor) {
			conductors--;
		}
		if (entry.canProvidePower()) {
			graph.removeSource(entry);
		} else {
			graph.removeNode(entry);
		}

		if (objects.isEmpty() || merging) return true;
		AbstractIterator<GridEntry> it = objItr;
		for (it.reset(); it.hasNext(); ) {
			it.next().gridUpdate();
		}

		Section box = this.box;
		if (box.isOnBorder(entry.pos())) {
			it.reset();
			entry = it.next();
			box.set(entry.pos(), entry.pos());

			while (it.hasNext()) {
				box.expandIf(it.next().pos());
			}
		}

		return true;
	}

	public void update() {
		if (updateTask()) return;

		if (routeDirty || generators == null) {
			generators = graph.compute(objects);
			valueDirty = true;
			routeDirty = false;
		}

		WaypointRenderer.reset();
		//if (valueDirty)
		graph.update();

		List<Path> generators = this.generators;
		for (int i = 0; i < generators.size(); i++) {
			Path gen = generators.get(i);

			boolean provided = false;
			Path[] sides = gen.getSides(tmp2);
			for (int j = 0; j < 6; j++) {
				Path path = sides[j];
				if (path == null) continue;

				Power p = gen.self.providePower(EnumFacing.VALUES[j]);
				// todo merge same power to a path
				if (p != null) {
					provided = true;

					double I = p.U / (p.R + gen.Rs);
					if (I > p.Imax && p.Imax != 0) {
						I = p.Imax;
					}
					p.I = I;

					path.flow(I * gen.Rs, I, new CircuitGraph.Context());
				}
			}

			if (provided) gen.U = 1;
		}

		valueDirty = false;

		for (int i = 0; i < generators.size(); i++) {
			generators.get(i).doFlow();
		}
	}

	// 并网
	public void merge(List<Grid> merges) {
		merging = true;
		for (int i = 0; i < merges.size(); i++) {
			Grid toMerge = merges.get(i);
			AbstractIterator<GridEntry> it = toMerge.objItr;
			for (it.reset(); it.hasNext(); ) {
				addNode(it.next());
			}
			box.expandIf(toMerge.box.min());
			box.expandIf(toMerge.box.max());
		}
		merging = false;
		owner.updatePosition(this);
	}

	public boolean isEmpty() {
		return !updateTask() && objects.isEmpty();
	}

	protected boolean updateTask() {
		if (task != null) {
			if (task.isFinished()) {
				refreshCallback(task);
				if (!task.isSucceed()) {
					ImpLib.logger().warn("网络过大! {} ", this);
				}
				owner.cancelOrEndTask(task);
				task = null;
			} else {
				return true;
			}
		}
		return false;
	}

	public void queueRefresh(BlockPos pos) {
		if (task != null) owner.cancelOrEndTask(task);
		task = owner.findPath(DimensionHelper.getWorldForDimension(null, world), pos);
		updateTask();
	}

	private void refreshCallback(PathFinder path) {
		objects.clear();
		routeDirty = true;

		MyHashSet<Grid> toMerge = new MyHashSet<>();

		MyHashSet<BlockPos> r = path.getResult();
		conductors = r.size();
		System.err.println(id);

		AbstractIterator<PathFinder.GridNode> itr = Helpers.cast(r.setItr());

		Section box = this.box;

		// 扩展边界
		if (itr.hasNext()) {
			BlockPos pos = itr.next();
			box.set(pos, pos);
			while (itr.hasNext()) {
				box.expandIf(itr.next());
			}
		}

		AbstractIterator<PathFinder.GridNode> itr1 = path.equipments.setItr();
		if (itr1.hasNext()) {
			if (r.isEmpty()) {
				BlockPos pos = itr1.next();
				box.set(pos, pos);
			}
			while (itr1.hasNext()) {
				box.expandIf(itr1.next());
			}
		}

		GetByPos objects = this.objects;

		// 存入导线
		itr.reset();
		while (itr.hasNext()) {
			PathFinder.GridNode node = itr.next();

			objects.add(node.entry);
			node.entry.enterGrid(this);
			graph.addNode(node.entry);

			toMerge.addAll(owner.getNearbyConnectable(world, node, this));
		}

		// 存入发电机/用电器
		for (itr1.reset(); itr1.hasNext(); ) {
			GridEntry entry = itr1.next().entry;

			if (objects.add(entry)) {
				if (entry.canProvidePower()) {
					graph.addSource(entry);
				} else {
					graph.addNode(entry);
				}
				entry.enterGrid(this);
			}
		}

		if (!toMerge.isEmpty()) {
			merging = true;
			for (Grid grid : toMerge) {
				owner.removeNetwork(grid);

				AbstractIterator<GridEntry> it = grid.objItr;
				for (it.reset(); it.hasNext(); ) {
					addNode(it.next());
				}
			}
			merging = false;
		}
		owner.updatePosition(this);
	}

	public void readConductors(Chunk c, IntSet components) {
		int x = c.x << 4;
		int z = c.z << 4;
		merging = true;
		for (IntIterator itr = components.iterator(); itr.hasNext(); ) {
			int off = itr.nextInt();

			TileEntity tile = c.getTileEntity(tmp.setPos(x + (off & 15), off >>> 8, z + ((off >> 4) & 15)), Chunk.EnumCreateEntityType.CHECK);

			if (tile == null) continue;
			GridEntry entry = owner.getGrid(tile, true);
			if (entry != null) addNode(entry);
		}
		merging = false;
		owner.updatePosition(this);
	}

	public boolean hasNearbyConductor(BlockPos pos) {
		if (conductors > 0) {
			for (EnumFacing facing : EnumFacing.VALUES) {
				if (objects.getByPos(tmp.setPos(pos).move(facing)) instanceof IConductor) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public Section getSection() {
		return box;
	}

	@Override
	public int getWorld() {
		return world;
	}

	@Override
	public String toString() {
		return "Grid{#" + id + ", at DIM" + world + ", " + box + ", obj=" + objects + '}';
	}
}
