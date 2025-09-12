package roj.compiler.runtime;

import roj.math.MathUtils;

/**
 * @author Roj234
 * @since 2024/5/31 6:18
 */
public final class SwitchMap {
	public static final class Builder {
		private static final class Entry {
			final Object k;
			final char v;

			Entry next;

			public Entry(Object k, int v) {
				this.k = k;
				this.v = (char) v;
			}
		}

		private final Entry[] entries;
		private final int mask;
		private int size;
		private final boolean useEquals;

		public static Builder builder(int size, boolean useEquals) { return new Builder(size, useEquals); }
		private Builder(int size, boolean useEquals) {
			this.useEquals = useEquals;
			if (size > 4096) throw new IllegalStateException("branch must <= 4096");

			this.entries = new Entry[MathUtils.nextPowerOfTwo(size)];
			this.mask = entries.length - 1;
		}

		/**
		 * 添加键值对到映射表，并检查重复键。
		 *
		 * <p><b>返回值特殊处理说明</b>：在生成的调用代码中，此返回值会与异常处理机制配合使用，
		 * 等效于以下伪代码：
		 * <pre>{@code
		 * try {
		 *     $$on_stack = add(key, ord); // 返回值压入操作数栈
		 * } catch ({@link roj.asm.insn.TryCatchBlock#ANY} e) {
		 *     // 异常时将异常压入操作数栈
		 *     $$on_stack = e;
		 * } finally {
		 *     pop(); // 忽略它们的返回值，无论是正常还是异常
		 * }
		 * }</pre>
		 *
		 * @param key 映射键（不可为 null）
		 * @param ord 键对应的序号（会转为 char 存储）
		 * @return 当前构造器实例（当且仅当你手写代码时支持链式调用）
		 * @throws IncompatibleClassChangeError 检测到重复键时抛出
		 */
		public Builder add(Object key, int ord) {
			int slot = (useEquals ? key.hashCode() : System.identityHashCode(key)) & mask;
			Entry entry = new Entry(key, ord);
			entry.next = entries[slot];
			entries[slot] = entry;

			// check duplicate case, for runtime
			entry = entry.next;
			while (entry != null) {
				if (useEquals ? key.equals(entry.k) : key == entry.k)
					throw new IncompatibleClassChangeError("case重复: "+key);
				entry = entry.next;
			}

			size++;
			return this;
		}

		public SwitchMap build() {
			char[] idx = new char[entries.length];
			Object[] pk = new Object[size];
			char[] pv = new char[size];

			int off = 0;

			for (int i = 0; i < entries.length; i++) {
				Entry entry = entries[i];
				int prevOff = off;
				while (entry != null) {
					pk[off] = entry.k;
					pv[off] = entry.v;
					off++;

					entry = entry.next;
				}

				// with an offset < 4096, and size < 16
				int len = off - prevOff;
				if (len > 15) throw new IllegalStateException("bad hash function");

				idx[i] = (char) ((prevOff << 4) | len);
			}

			return new SwitchMap(idx, pk, pv, useEquals);
		}
	}

	private final char[] idx, pv;
	private final Object[] pk;
	private final int mask;
	private final boolean useEquals;

	private SwitchMap(char[] idx, Object[] pk, char[] pv, boolean useEquals) {
		this.idx = idx;
		this.pk = pk;
		this.pv = pv;
		this.mask = idx.length - 1;
		this.useEquals = useEquals;
	}

	public int get(Object o) {
		int slot = idx[(useEquals ? o.hashCode() : System.identityHashCode(o)) & mask];

		int off = slot >>> 4;
		int end = off + (slot&15);
		while (off < end) {
			Object k = pk[off];
			if (useEquals ? o.equals(k) : o == k) return pv[off];

			off++;
		}
		return 0;
	}
}