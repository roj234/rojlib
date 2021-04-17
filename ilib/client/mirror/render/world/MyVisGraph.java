package ilib.client.mirror.render.world;

import roj.collect.IntList;
import roj.collect.MyBitSet;

import net.minecraft.client.renderer.chunk.SetVisibility;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;
import java.util.Set;

/**
 * @author Roj233
 * @since 2022/4/22 13:22
 */
public class MyVisGraph {
	private static final int DX = 1;
	private static final int DZ = 16;
	private static final int DY = 16 * 16;

	private static final int[] EdgeIndexes = new int[1352];

	private final MyBitSet opaque = new MyBitSet(4096);
	private int free = 4096;

	private final IntList b0, b1;
	private final EnumSet<EnumFacing> set = EnumSet.noneOf(EnumFacing.class);

	public MyVisGraph() {
		b0 = new IntList();
		b1 = new IntList();
	}

	public void setOpaqueCube(BlockPos pos) {
		opaque.add(idx(pos));
		free--;
	}

	private static int idx(BlockPos pos) {
		return idx(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
	}

	private static int idx(int x, int y, int z) {
		return x | y << 8 | z << 4;
	}

	public SetVisibility compute() {
		SetVisibility sv = new SetVisibility();
		if (free > 4096 - 256) {
			sv.setAllVisible(true);
			for (int i : EdgeIndexes) {
				if (!opaque.contains(i)) traverse(i);
			}
		} else if (free == 0) {
			sv.setAllVisible(false);
			for (int i : EdgeIndexes) {
				if (!opaque.contains(i)) traverse(i);
			}
		} else {
			for (int i : EdgeIndexes) {
				if (!opaque.contains(i)) {
					sv.setManyVisible(traverse(i));
				}
			}
		}

		return sv;
	}

	public Set<EnumFacing> getVisibleFacings(BlockPos pos) {
		set.clear();

		int idx = idx(pos);
		for (EnumFacing face : EnumFacing.VALUES) {
			int nb = neighbor(idx, face);
			if (nb < 0 || !opaque.contains(nb)) {
				set.add(face.getOpposite());
			}
		}
		return set;
	}

	public void clear() {
		opaque.clear();
	}

	private Set<EnumFacing> traverse(int pos) {
		set.clear();

		IntList buf = b0;
		IntList next = b1;

		buf.clear();
		buf.add(pos);

		opaque.add(pos);

		while (!buf.isEmpty()) {
			for (int i = 0; i < buf.size(); i++) {
				int off = buf.get(i);
				addEdges(off, set);
				for (EnumFacing face : EnumFacing.VALUES) {
					int idx = neighbor(off, face);
					if (idx >= 0 && opaque.add(idx)) {
						next.add(idx);
					}
				}
			}
			IntList t = buf;
			buf = next;
			next = t;
			next.clear();
		}

		return set;
	}

	private static void addEdges(int pos, Set<EnumFacing> set) {
		int x = pos & 15;
		if (x == 0) {
			set.add(EnumFacing.WEST);
		} else if (x == 15) {
			set.add(EnumFacing.EAST);
		}

		int y = pos >> 8 & 15;
		if (y == 0) {
			set.add(EnumFacing.DOWN);
		} else if (y == 15) {
			set.add(EnumFacing.UP);
		}

		int z = pos >> 4 & 15;
		if (z == 0) {
			set.add(EnumFacing.NORTH);
		} else if (z == 15) {
			set.add(EnumFacing.SOUTH);
		}
	}

	private static int neighbor(int pos, EnumFacing facing) {
		switch (facing) {
			case DOWN:
				if ((pos >> 8 & 15) == 0) {
					return -1;
				}

				return pos - DY;
			case UP:
				if ((pos >> 8 & 15) == 15) {
					return -1;
				}

				return pos + DY;
			case NORTH:
				if ((pos >> 4 & 15) == 0) {
					return -1;
				}

				return pos - DZ;
			case SOUTH:
				if ((pos >> 4 & 15) == 15) {
					return -1;
				}

				return pos + DZ;
			case WEST:
				if ((pos & 15) == 0) {
					return -1;
				}

				return pos - DX;
			case EAST:
				if ((pos & 15) == 15) {
					return -1;
				}

				return pos + DX;
			default:
				return -1;
		}
	}

	static {
		int i = 0;
		for (int x = 0; x < 16; ++x) {
			for (int y = 0; y < 16; ++y) {
				for (int z = 0; z < 16; ++z) {
					if (x == 0 || x == 15 || y == 0 || y == 15 || z == 0 || z == 15) {
						EdgeIndexes[i++] = idx(x, y, z);
					}
				}
			}
		}
	}
}
