package roj.collect;

import static java.lang.Integer.lowestOneBit;

/**
 * @author Roj234
 * @since 2023/1/21 0021 10:08
 */
public class SumArray {
	public static final int RANGE_ADD = 1, RANGE_SUM = 2;

	final long[] tree;
	final byte flag;

	public SumArray(int cap, int characteristics) {
		if (characteristics == 3) cap <<= 1;
		tree = new long[cap];
		this.flag = (byte) characteristics;
	}
	public SumArray(long[] list, int characteristics) {
		this.flag = (byte) characteristics;
		if (characteristics == 3) {
			tree = new long[list.length<<1];
			System.arraycopy(list, 0, tree, 0, list.length);
			_init(tree, 0, list.length);
			System.arraycopy(tree, 0, tree, list.length, list.length);
		} else {
			// init optimization as O(n) <=> update n times = n*logN
			tree = list.clone();
			_init(tree, 0, tree.length);
		}
	}

	private static void _init(long[] tree, int off, int len) {
		off--;
		for (int i = 1; i <= len; i++) {
			int j = i + lowestOneBit(i);
			if (j <= len) tree[j+off] += tree[i+off];
		}
	}
	private static void _add(long[] l, int off, int len, int i, long v) {
		off--;
		for (i++; i <= len; i += lowestOneBit(i)) l[i+off] += v;
	}
	private static long _sum(long[] l, int off, int i) {
		off--;
		long v = 0;
		for (i++; i != 0; i -= lowestOneBit(i)) v += l[i+off];
		return v;
	}

	public void plus(int i, long v) {
		_add(tree, 0, tree.length, i, v);
	}
	/**
	 * @param to is inclusive
	 */
	public void plus(int from, int to, long v) {
		if ((flag&RANGE_ADD) == 0) throw new UnsupportedOperationException();
		if (to < from) throw new IndexOutOfBoundsException("to < from");
		int len = tree.length;

		if (flag == 3) {
			_add(tree, len/2, len, from, v*(from-1));
			_add(tree, len/2, len, to+1, -v*to);

			len /= 2;
		}

		if (to >= len) throw new IndexOutOfBoundsException("to > array size");
		_add(tree, 0, len, from, v);
		_add(tree, 0, len, to+1, -v);
	}

	/**
	 * Σ[0,to] (inclusive)
	 */
	public long sum(int to) {
		return _sum(tree, 0, to);
	}
	/**
	 * Σ[from,to] (inclusive)
	 */
	public long sum(int from, int to) {
		switch (flag) {
			default:
			case 0:
			case 2: return sum(to) - sum(from-1);
			case 1: throw new UnsupportedOperationException();
			case 3:
				int half = tree.length/2;
				return (to * _sum(tree, 0, to) - _sum(tree, half, to)) -
					((from-1) * _sum(tree, 0, from-1) - _sum(tree, half, from-1));
		}
	}
}
