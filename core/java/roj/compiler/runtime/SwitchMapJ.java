package roj.compiler.runtime;

import roj.math.MathUtils;

/**
 * 这个实现采用开放寻址法，比闭合寻址的SwitchMap慢一丢丢
 * 但是因为修改了数据结构，所以更省内存
 * @author Roj234
 * @since 2025/4/9 17:05
 */
public final class SwitchMapJ {
	public static final class Builder {
		private final long[] keys;
		private final int[] tab;
		private final int mask;

		public static Builder builder(int size) {return new Builder(size);}
		private Builder(int size) {
			keys = new long[size];
			mask = MathUtils.nextPowerOfTwo((int)(size * 1.5f))-1; // 负载因子≈66%
			tab = new int[mask+1];
		}

		public Object add(long key, int ord) {
			int idx = hash(key) & mask;
			while (true) {
				int slotVal = tab[idx];
				if (slotVal == 0) {
					keys[ord] = key;
					tab[idx] = ord+1;
					return null;
				} else if (keys[slotVal - 1] == key) {
					throw new IllegalArgumentException("Duplicate key: " + key);
				}
				idx = (idx + 1) & mask; // 线性探测下一个槽位
			}
		}

		public SwitchMapJ build() {return new SwitchMapJ(keys, tab, mask);}
	}

	private final long[] keys;
	private final int[] tab;
	private final int mask;

	private SwitchMapJ(long[] keys, int[] tab, int mask) {
		this.keys = keys;
		this.tab = tab;
		this.mask = mask;
	}

	public int get(long key) {
		int idx = hash(key) & mask;
		while (true) {
			int slotVal = tab[idx]-1;
			if (slotVal < 0) return -1;
			if (keys[slotVal] == key) return slotVal;
			idx = (idx + 1) & mask;
		}
	}

	static int hash(long key) {
		return (int)(key ^ (key >>> 32));// * 0x9E3779B9; // 混合哈希
	}
}