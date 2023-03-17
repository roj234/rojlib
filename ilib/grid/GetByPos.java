package ilib.grid;

import roj.collect.MyHashSet;

import net.minecraft.util.math.Vec3i;

/**
 * @author Roj233
 * @since 2022/5/14 13:33
 */
final class GetByPos extends MyHashSet<GridEntry> {
	public GridEntry getByPos(int x, int y, int z) {
		if (entries == null) return null;

		Object obj = entries[hash(x, y, z) & mask];
		while (obj instanceof Entry) {
			Entry prev = (Entry) obj;
			Vec3i pos = ((GridEntry) prev.k).pos();
			if (pos.getX() == x && pos.getY() == y && pos.getZ() == z) return (GridEntry) prev.k;
			obj = prev.next;
		}

		if (obj != null) {
			Vec3i pos = ((GridEntry) obj).pos();
			if (pos.getX() == x && pos.getY() == y && pos.getZ() == z) return (GridEntry) obj;
		}
		return null;
	}

	public GridEntry getByPos(Vec3i pos) {
		if (entries == null) return null;

		Object obj = entries[pos.hashCode() & mask];
		while (obj instanceof Entry) {
			Entry prev = (Entry) obj;
			if (pos.equals(((GridEntry) prev.k).pos())) return (GridEntry) prev.k;
			obj = prev.next;
		}
		if (obj != null && pos.equals(((GridEntry) obj).pos())) return (GridEntry) obj;
		return null;
	}

	private static int hash(int x, int y, int z) {
		return (y + z * 31) * 31 + x;
	}

	@Override
	protected boolean eq(GridEntry id, Object k) {
		return k != null && id.pos().equals(((GridEntry) k).pos());
	}

	@Override
	protected int indexFor(GridEntry id) {
		return id.pos().hashCode();
	}
}
