package roj.util;

import roj.collect.MyBitSet;
import roj.collect.RSegmentTree;
import roj.collect.RSegmentTree.Point;
import roj.collect.RSegmentTree.Range;
import roj.collect.RSegmentTree.Region;
import roj.text.CharList;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 根据变量作用域分配可重用的ID
 *
 * @author Roj233
 * @since 2022/2/22 23:55
 */
public class VarMapper {
	final RSegmentTree<IVar> union = new RSegmentTree<>(10, false, 100);
	final MyBitSet freeIds = new MyBitSet();
	final List<IVar> tmp = new ArrayList<>();

	public final void dump(List<? extends IVar> list, int width) {
		MyBitSet free = freeIds;
		free.clear();
		RSegmentTree<IVar> union = this.union;
		union.clear();
		union.setCare(true);

		for (int i = 0; i < list.size(); i++) {
			union.add(list.get(i));
		}

		PrintStream pr = System.out;
		CharList sb = new CharList();

		int height = 0;
		for (int i = 0; i < list.size(); i++) {
			height = Math.max((int) list.get(i).endPos(), height);
		}
		int pad = Integer.toString(height).length();

		for (int i = 0; i < height; i++) {
			List<IVar> v1 = union.collect(i);

			free.clear();
			for (int j = 0; j < v1.size(); j++) {
				free.add(v1.get(j).slot());
			}

			String s = Integer.toString(i);
			sb.append(s);
			for (int k = pad - s.length(); k >= 0; k--) {
				sb.append(' ');
			}

			for (int j = 0; j < width; j++) {
				sb.append(free.contains(j) ? '*' : ' ');
			}
			pr.println(sb);
			sb.clear();
		}

		union.clear();
		union.setCare(false);
	}

	public void add(IVar v1) {
		union.add(v1);
	}

	public void setCare(boolean care) {
		union.setCare(care);
	}

	public void clear() {
		union.clear();
	}

	public int regionCount() {
		return union.arraySize();
	}

	public int map(List<? extends IVar> list) {
		MyBitSet free = freeIds;
		RSegmentTree<IVar> union = this.union;
		List<IVar> tmp = this.tmp;

		if (list != null) {
			for (int i = 0; i < list.size(); i++) {
				union.add(list.get(i));
			}
		}

		int id = 0;
		int peakId = id;

		Region prev = null;
		Region[] array = union.dataArray();
		for (int j = 0; j < union.arraySize(); j++) {
			onRegion(prev, prev = array[j]);
			Point point = array[j].node();
			while (point != null) {
				IVar v = point.owner();
				if (point.end()) {
					if (v.slot() == id) {
						id--;
					} else {
						free.add(v.slot());
					}
				} else {
					tmp.add(point.owner());
				}
				point = point.next();
			}

			for (int i = 0; i < tmp.size(); i++) {
				IVar v = tmp.get(i);
				int last = free.first();
				if (last < 0) {
					v.slot(id++);
				} else {
					free.remove(last);
					v.slot(last);
				}

				if (id > peakId) {
					peakId = id;
				}
			}
			tmp.clear();
		}
		free.clear();

		return peakId;
	}

	protected void onRegion(Region prev, Region r) {}

	public interface IVar extends Range {
		int slot();

		void slot(int slot);
	}

	public static class Var implements IVar {
		public int start, end, slot;
		public Object att;

		@Override
		public long startPos() {
			return start;
		}

		@Override
		public long endPos() {
			return end;
		}

		@Override
		public int slot() {
			return slot;
		}

		@Override
		public void slot(int slot) {
			this.slot = slot;
		}

		@Override
		public String toString() {
			return "Var{" + "[" + start + "," + end + "]@" + slot + '}';
		}
	}
}
