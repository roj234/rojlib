package roj.compiler.runtime;

import roj.math.MathUtils;

/**
 * @author Roj234
 * @since 2025/4/9 0009 17:55
 */
public final class SwitchMapI {
	public static final class Builder {
		private final int[] keys;
		private final int[] tab;
		private final int mask;

		public static Builder builder(int size) {return new Builder(size);}
		private Builder(int size) {
			keys = new int[size];
			mask = MathUtils.getMin2PowerOf((int)(size * 1.5f)); // 负载因子≈66%
			tab = new int[mask+1];
		}

		public Object add(int key, int ord) {
			int idx = hash(key) & mask;
			while (true) {
				int slotVal = tab[idx];
				if (slotVal == 0) {
					keys[ord] = key;
					tab[idx] = ord; // 存储索引+1（避免0冲突）
					return null;
				} else if (keys[slotVal - 1] == key) {
					throw new IllegalArgumentException("Duplicate key: " + key);
				}
				idx = (idx + 1) & mask; // 线性探测下一个槽位
			}
		}

		public SwitchMapI build() {return new SwitchMapI(keys, tab, mask);}
	}

	private final int[] keys;
	private final int[] tab;
	private final int mask;

	private SwitchMapI(int[] keys, int[] tab, int mask) {
		this.keys = keys;
		this.tab = tab;
		this.mask = mask;
	}

	public int get(int key) {
		int idx = hash(key) & mask;
		while (true) {
			int slotVal = tab[idx];
			if (slotVal == 0) return -1; // 未找到
			if (keys[slotVal - 1] == key) return slotVal - 1; // 返回索引
			idx = (idx + 1) & mask; // 继续探测
		}
	}

	static int hash(int key) {
		return key * 0x9E3779B9; // 混合哈希
	}
}