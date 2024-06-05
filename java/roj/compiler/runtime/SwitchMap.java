package roj.compiler.runtime;

import roj.math.MathUtils;

/**
 * @author Roj234
 * @since 2024/5/31 0031 6:18
 */
public final class SwitchMap {
	public static final class Builder {
		static final class Entry {
			final Object k;
			final char v;

			Entry next;

			public Entry(Object k, int v) {
				this.k = k;
				this.v = (char) v;
			}
		}

		private final Entry[] entries;
		private final int mask, size;
		private final boolean useEquals;

		public static Builder builder(int size, boolean useEquals) { return new Builder(size, useEquals); }

		public Builder(int size, boolean useEquals) {
			this.size = size;
			this.useEquals = useEquals;
			if (size > 4096) throw new IllegalStateException("branch must <= 4096");

			this.entries = new Entry[MathUtils.getMin2PowerOf(size)];
			this.mask = entries.length - 1;
		}

		public Object add(Object o, int v) {
			int slot = (useEquals ? o.hashCode() : System.identityHashCode(o)) & mask;
			Entry entry = new Entry(o, v);
			entry.next = entries[slot];
			entries[slot] = entry;

			// check duplicate case, for runtime
			entry = entry.next;
			while (entry != null) {
				if (useEquals ? o.equals(entry.k) : o == entry.k)
					throw new IncompatibleClassChangeError("case重复: "+o);
				entry = entry.next;
			}

			return null;
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

	public SwitchMap(char[] idx, Object[] pk, char[] pv, boolean useEquals) {
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