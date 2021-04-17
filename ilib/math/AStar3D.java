package ilib.math;

import roj.collect.BSLowHeap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.math.Vec3i;
import roj.util.ArrayUtil;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/2/2 18:44
 */
public abstract class AStar3D {
	private final int cacheSize;
	private final SimpleList<Point> cache;

	protected final BSLowHeap<Point> open = new BSLowHeap<>(PointCpr.INSTANCE), tmp = new BSLowHeap<>(PointCpr.INSTANCE);
	public List<Point> result;

	protected final MyHashSet<Point> closed = new MyHashSet<>();

	protected final Point retainP(int x, int y, int z) {
		if (!cache.isEmpty()) return (Point) cache.remove(cache.size() - 1).set(x, y, z);
		return new Point(x, y, z);
	}

	protected final void releaseP(Point pos) {
		if (cache.size() < cacheSize) cache.add(pos);
	}

	public AStar3D() {
		this(256);
	}

	public AStar3D(int cache) {
		cacheSize = cache - 1;
		this.cache = new SimpleList<>();
	}

	public final List<Point> find(BlockPos from, BlockPos to) {
		return find(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
	}

	public List<Point> find(int strX, int strY, int strZ, int endX, int endY, int endZ) {
		result = null;
		if (strX == endX && strY == endY && strZ == endZ) return result = Collections.singletonList(retainP(strX, strY, strZ));

		BSLowHeap<Point> open = this.open;
		open.clear();
		MyHashSet<Point> closed = this.closed;
		closed.clear();
		BSLowHeap<Point> tmp = this.tmp;
		tmp.clear();

		long startTime = System.currentTimeMillis();

		Point str = retainP(strX, strY, strZ);
		open.add(str);

		Point end = retainP(endX, endY, endZ);

		int i = 0;
		while (canWalk(i++)) {
			if (open.isEmpty()) break;

			if (open.get(0).equals(end)) {
				for (Point pos : closed) {
					releaseP(pos);
				}
				for (int j = 0; j < open.size(); j++) {
					Point node = open.get(j);
					if (!closed.remove(node)) {
						releaseP(node);
					}
				}
				return result = resolvePath(open.get(0));
			}

			for (int j = 0; j < open.size(); j++) {
				Point pos = open.get(j);
				int x = pos.x;
				int y = pos.y;
				int z = pos.z;
				for (EnumFacing side : EnumFacing.VALUES) {
					check(pos, tmp, side, x, y, z, end);
				}
			}

			open.clear();

			BSLowHeap<Point> tmp1 = open;
			open = tmp;
			tmp = tmp1;
		}

		for (Point pos : closed) {
			releaseP(pos);
		}

		return null;
	}

	protected final void check(Point parent, BSLowHeap<Point> list, EnumFacing side, int x, int y, int z, Point end) {
		x += side.getXOffset();
		y += side.getYOffset();
		z += side.getZOffset();

		float cost = cost(x, y, z, side);
		if (cost > Float.MAX_VALUE) return;

		Point node = retainP(x, y, z);

		node.parent = parent;
		node.cost = parent.cost + cost;

		//如果访问到开放列表中的点，则将此点重置为消耗最小的路径,否则添加到开放列表
		int i = list.indexOf(node);
		if (i != -1) {
			Point in = list.get(i);
			if (node.cost < in.cost) {
				in.parent = parent;
				in.cost = node.cost;
				in.distant = node.distant;
			}
			releaseP(node);
		} else {
			// 已在关闭列表则不做处理
			if (!closed.add(node)) {
				releaseP(node);
				return;
			}

			node.distant = distance(node, end);

			list.add(node);
		}
	}

	protected double distance(Point a, Point b) {
		return a.distanceSq(b);
	}

	public abstract boolean canWalk(int i);

	protected abstract float cost(int x, int y, int z, EnumFacing sideFrom);

	protected static List<Point> resolvePath(Point node) {
		SimpleList<Point> path = new SimpleList<>();
		do {
			if (path.contains(node.parent)) throw new Error();
			path.add(node);
		} while ((node = node.parent) != null);
		ArrayUtil.inverse(path.getRawArray(), path.size());

		return path;
	}

	public static class PointCpr implements Comparator<Point> {
		public static final PointCpr INSTANCE = new PointCpr();

		@Override
		public int compare(Point o1, Point o2) {
			return Double.compare(o1.cost + o1.distant, o2.cost + o2.distant);
		}
	}

	public static final class Point extends Vec3i {
		public Point parent;
		public float cost;
		public double distant;

		@Override
		public Vec3i set(int x, int y, int z) {
			parent = null;
			cost = 0;
			distant = 0;
			return super.set(x, y, z);
		}

		public Point(int x, int y, int z) {
			super(x, y, z);
		}
	}
}
