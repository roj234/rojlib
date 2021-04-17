package ilib.grid;

import ilib.client.renderer.WaypointRenderer;
import roj.collect.*;
import roj.util.ArrayUtil;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/5/14 17:52
 */
final class CircuitGraph extends Graph<GridEntry> {
	static final Cache DEFAULT = new Cache();
	Cache cache = DEFAULT;

	private final MyBitSet sources = new MyBitSet();
	private final IntIterator srcItr = sources.iterator();

	private final SimpleList<Path> root = new SimpleList<>();

	public void addSource(GridEntry entry) {
		addNode(entry);
		sources.add(getId(entry));
	}

	public void removeSource(GridEntry entry) {
		sources.remove(getId(entry));
		removeNode(entry);
	}

	public List<Path> compute(GetByPos entries) {
		// 没有用到边, 因为连接是从entries提取的

		IntMap<Path> closed = cache.closed;

		List<Path> paths = root;
		for (int i = 0; i < paths.size(); i++) {
			releasePath(paths.get(i), true);
		}
		paths.clear();

		IntIterator itr = srcItr;
		for (itr.reset(); itr.hasNext(); ) {
			closed.clear();
			Path path = retainPath(elements.get(itr.nextInt()));
			if (DFS(path, entries, closed, -1)) {
				paths.add(path);
			} else {
				releasePath(path, true);
			}
		}

		return paths;
	}

	public void update() {
		List<Path> paths = root;
		MyBitSet v = cache.visitor;
		v.clear();
		for (int i = 0; i < paths.size(); i++) {
			paths.get(i).compute(null);
		}
	}

	private boolean DFS(Path parent, GetByPos entries, IntMap<Path> closed, int from) {
		BlockPos pos = parent.self.pos();
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		boolean done = false;

		EnumFacing[] values = EnumFacing.VALUES;
		for (int i = 0; i < values.length; i++) {
			if (i == from) continue;

			EnumFacing side = values[i];
			// 减，其实就相当于给side取了opposite
			GridEntry next = entries.getByPos(x - side.getXOffset(), y - side.getYOffset(), z - side.getZOffset());
			if (next == null) continue;
			int id = getId(next);

			IntMap.Entry<Path> stored = closed.getOrCreateEntry(id);
			if (stored.getValue() == null) {
				continue;
			} else if (stored.getValue() != IntMap.UNDEFINED) {
				done = true;
				Path path = stored.getValue();
				path.toHere++;
				parent.minDist = Math.min(path.minDist + 1, parent.minDist);
				parent.append(path);
				continue;
			}
			stored.setValue(null);

			Path path = retainPath(next);
			path.sideFrm = (byte) i;
			path.toHere = 1;

			if (next instanceof IConductor) {
				if (DFS(path, entries, closed, side.getOpposite().ordinal())) {
					done = true;
					parent.minDist = Math.min(path.minDist + 1, parent.minDist);
					parent.append(path);
					stored.setValue(path);
					continue;
				}
			} else {
				if (next.canConsumePower()) {
					done = true;

					closed.remove(id);
					Path path1 = closed.get((side.ordinal() << 29) | id);
					if (path1 == null) {
						path.minDist = 0;
						parent.minDist = 1;
						parent.append(path);
						closed.putInt((side.ordinal() << 29) | id, path);
						continue;
					}

					parent.minDist = Math.min(path1.minDist + 1, parent.minDist);
					parent.append(path1);
				}
			}

			closed.remove(id);
			releasePath(path, false);
		}
		return done;
	}

	private Path retainPath(GridEntry entry) {
		SimpleList<Path> cache = this.cache.cache;
		if (!cache.isEmpty()) {
			Path path = cache.remove(cache.size() - 1);
			path.self = entry;
			return path;
		}
		return new Path(entry);
	}

	private void releasePath(Path entry, boolean recursion) {
		SimpleList<Path> cache = this.cache.cache;
		if (recursion && cache.contains(entry)) return;

		Path[] child = entry.child;
		for (int i = entry.childCount - 1; i >= 0; i--) {
			Path p = child[i];
			if (p != null) {
				if (recursion) releasePath(p, true);
				child[i] = null;
			}
		}
		entry.childCount = 0;
		entry.self = null;
		entry.sideFrm = -1;
		entry.R = entry.Rs = Double.NaN;
		entry.U = 0;
		entry.toHere = 0;
		entry.minDist = 99999;
		cache.add(entry);
	}

	static class Context {
		List<Path> crossPair = new SimpleList<>();
		MyBitSet state = new MyBitSet();
	}

	static final class Path {
		Path[] child;
		int childCount;

		int toHere, minDist = 99999;
		public GridEntry self;
		public byte sideFrm = -1;
		public double R, Rs = Double.NaN;
		double U;

		public Path(GridEntry entry) {
			self = entry;
		}

		public void append(Path path1) {
			if (child == null) child = new Path[6];
			child[childCount++] = path1;
		}

		public Path[] getSides(Path[] frm) {
			Path[] sided = frm == null ? new Path[6] : frm;
			Path[] child = this.child;
			for (int i = 0; i < childCount; i++) {
				sided[getSide(self.pos(), child[i].self.pos()).ordinal()] = child[i];
			}
			return sided;
		}

		public void flow(double U, double I, Context ctx) {
			double u = U * R / Rs;
			this.U += u;
			U -= u;

			Path[] child = this.child;
			double Rs = 0;
			for (int i = childCount - 1; i >= 0; i--) {
				Rs += 1 / child[i].Rs;
			}

			for (int i = childCount - 1; i >= 0; i--) {
				child[i].flow(U, I / child[i].Rs / Rs, ctx);
			}
		}

		public void doFlow() {
			if (U <= 0) return;

			if (childCount == 0) {
				self.consumePower((float) U, U / R);
			} else {
				if (self instanceof IConductor) {
					((IConductor) self).onEnergyFlow((float) U, U / R);
				}

				Path[] child = this.child;
				for (int i = childCount - 1; i >= 0; i--) {
					child[i].doFlow();
				}
			}

			U = 0;
		}

		public void compute() {
			MyHashSet<Path> curr = new MyHashSet<>();
			MyHashSet<Path> next = new MyHashSet<>();
			SimpleList<Path> stack = new SimpleList<>();
			SimpleList<Path> done = new SimpleList<>();

			AbstractIterator<Path> cItr = curr.setItr();
			AbstractIterator<Path> nItr = next.setItr();

			curr.add(this);

			while (!curr.isEmpty()) {
				cItr.reset();
				while (cItr.hasNext()) {
					Path pos = cItr.next();

					if (pos.toHere > 1) stack.add(pos);
					if (pos.childCount > 1) stack.add(pos);

					for (int k = 0; k < pos.childCount; k++) {
						if (next.add(pos.child[k])) {
							done.add(pos.child[k]);
						}
					}
				}

				MyHashSet<Path> tmp = curr;
				curr = next;
				next = tmp;
				next.clear();

				AbstractIterator<Path> tmp2 = cItr;
				cItr = nItr;
				nItr = tmp2;
			}

			for (int i = 0; i < stack.size(); i++) {
				stack.get(i).dsp("cross", "Cross #" + i, -2);
			}
		}

		public double compute(Path from) {
			R = from == null ? 0 : self.getResistance(EnumFacing.VALUES[sideFrm]);
			if (childCount == 0) {
				dsp("Res", "R=" + (float) R, -1);
				return Rs = R;
			}

			int locked = 0;
			double up = 1;
			double down = 0;

			if (childCount > 1) {
				Rs = Double.NaN;

				ToIntMap<Path> closed = new ToIntMap<>();
				iterateNearest(closed);
				locked = findNearestCEND(this, closed);
				if (Rs == Rs) {
					up = Rs;
					down = 1;
				}
			}

			Path[] child = this.child;
			for (int i = childCount - 1; i >= 0; i--) {
				if ((locked & (1 << i)) != 0) continue;

				double R1 = child[i].compute(this);
				// 并联电阻公式
				down = up + down * R1;
				up *= R1;
			}
			dsp("R", "R=" + (float) (up / down + R), -1);
			return Rs = up / down + R;
		}

		private int findNearestCEND(Path path, ToIntMap<Path> closed) {
			int lock = 0;
			double up = 1;
			double down = 0;

			Path[] child = this.child;
			for (int k = childCount - 1; k >= 0; k--) {
				Path p = child[k];
				if (p.minDist >= minDist) continue;

				if (closed.getInt(p) > 1) {
					p.dsp("sum", "s=" + closed.getInt(p), 1);
					return 0;
				} else {
					double R1 = p.compute(this);
					down = up + down * R1;
					up *= R1;
					lock |= 1 << k;
				}

				int lock1 = p.findNearestCEND(null, closed);
				if (lock1 != 0) {
					return 0;
				}
			}
			if (lock != 0 && path != null) Rs = up / down;
			dsp("L", Integer.toBinaryString(lock), 0);
			return lock;
		}

		private void iterateNearest(ToIntMap<Path> closed) {
			closed.increase(this, 1);

			Path[] child = this.child;
			for (int k = childCount - 1; k >= 0; k--) {
				Path p = child[k];
				if (p.minDist >= minDist) continue;
				p.iterateNearest(closed);
			}
		}

		private void dsp(String type, String msg, int yOffset) {
			BlockPos p = self.pos();
			WaypointRenderer.addIfNotPresent(type + p, msg, p.getX(), p.getY() + yOffset, p.getZ(), null, -1);
		}

		private static EnumFacing getSide(BlockPos from, BlockPos to) {
			if (from.getY() != to.getY()) {
				return from.getY() < to.getY() ? EnumFacing.DOWN : EnumFacing.UP;
			}
			if (from.getZ() != to.getZ()) {
				return from.getZ() < to.getZ() ? EnumFacing.NORTH : EnumFacing.SOUTH;
			}
			if (from.getX() != to.getX()) {
				return from.getX() < to.getX() ? EnumFacing.WEST : EnumFacing.EAST;
			}
			throw new IllegalStateException();
		}

		@Override
		public String toString() {
			return "{" + self + ", R=" + R + ", " + ArrayUtil.toString(child, 0, childCount) + '}';
		}
	}

	static final class Cache {
		final SimpleList<Path> cache = new SimpleList<>();
		final IntMap<Path> closed = new IntMap<>();
		final MyBitSet visitor = new MyBitSet();
	}
}
