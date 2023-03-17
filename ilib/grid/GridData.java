package ilib.grid;

import ilib.math.PositionProvider;
import ilib.math.Section;
import roj.collect.AbstractIterator;
import roj.collect.IntSet;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.util.ByteList;

import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;

import java.util.Iterator;

/**
 * @author Roj233
 * @since 2022/5/16 1:56
 */
final class GridData implements PositionProvider {
	private final int world;
	private final Section box;
	private SimpleList<IntSet> networks;

	GridData(int world, Section box) {
		this.world = world;
		this.box = box;
	}

	@Override
	public Section getSection() {
		return box;
	}

	@Override
	public int getWorld() {
		return world;
	}

	public boolean isEmpty() {
		return networks.isEmpty();
	}

	public IntSet get(BlockPos pos) {
		int off = (pos.getY() << 8) | ((pos.getX() & 15) << 4) | (pos.getZ() & 15);

		SimpleList<IntSet> networks = this.networks;
		for (int i = 0; i < networks.size(); i++) {
			IntSet set = networks.get(i);
			if (set.contains(off)) {
				networks.remove(i);
				return set;
			}
		}

		return null;
	}

	public void readFromNBT(NBTTagList list) {
		ByteList tmp = new ByteList();
		networks = new SimpleList<>(list.tagCount());
		for (int i = 0; i < list.tagCount(); i++) {
			tmp.setArray(((NBTTagByteArray) list.get(i)).getByteArray());
			IntSet set = new IntSet();
			while (tmp.isReadable()) {
				set.add(tmp.readVarInt(false));
			}
			networks.add(set);
		}
	}

	public static NBTTagList writeToNBT(Section box, Iterator<Grid> grids) {
		ByteList tmp = IOUtil.getSharedByteBuf();
		NBTTagList list = new NBTTagList();
		while (grids.hasNext()) {
			Grid grid = grids.next();
			AbstractIterator<GridEntry> it = grid.objItr;
			for (it.reset(); it.hasNext(); ) {
				GridEntry next = it.next();
				if (!(next instanceof IConductor)) continue;

				BlockPos pos = next.pos();
				if (box.contains(pos)) {
					int off = (pos.getY() << 8) | ((pos.getX() & 15) << 4) | (pos.getZ() & 15);
					tmp.putVarInt(off, false);
				}
			}

			if (tmp.wIndex() > 0) {
				list.appendTag(new NBTTagByteArray(tmp.toByteArray()));
				tmp.clear();
			}
		}
		return list;
	}
}
