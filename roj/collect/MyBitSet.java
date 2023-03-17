package roj.collect;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.NoSuchElementException;

import static roj.collect.AbstractIterator.*;

/**
 * @author Roj234
 * @since 2021/5/11 23:9
 */
public class MyBitSet implements Iterable<Integer> {
	protected long[] set;
	protected int cap;
	protected int max = -1;
	protected int size;

	public MyBitSet() {
		this(1);
	}

	public MyBitSet(int size) {
		this.set = new long[(size >> 6) + 1];
		this.cap = set.length << 6;
	}

	public MyBitSet(long[] set, int count) {
		this.set = set;
		this.cap = set.length << 6;
		this.size = count;
		if (count > 0) {
			this.max = cap;
			this.max = prevTrue(cap-1);
		}
	}

    public static MyBitSet from(CharSequence data) {
		MyBitSet set = new MyBitSet(data.length());
		for (int i = 0; i < data.length(); i++) {
			set.add(data.charAt(i));
		}
		return set;
	}

	public static MyBitSet from(byte... data) {
		MyBitSet set = new MyBitSet(data.length);
		for (byte i : data) {
			set.add(i);
		}
		return set;
	}

	public static MyBitSet from(int... data) {
		MyBitSet set = new MyBitSet(data.length);
		for (int i : data) {
			set.add(i);
		}
		return set;
	}

	public static MyBitSet fromRange(int from, int to) {
		MyBitSet set = new MyBitSet(to);
		set.addRange(from, to);
		return set;
	}

	public boolean contains(int key) {
		if (key < 0 || (key >>> 6) >= set.length) return false;
		return (set[key >>> 6] & (1L << (key & 63))) != 0;
	}

	private void expand(int i) {
		if (i >= cap) {
			long[] newSet = new long[(i >> 6) + 1];

			this.cap = newSet.length << 6;
			System.arraycopy(set, 0, newSet, 0, set.length);

			this.set = newSet;
		}
	}

	public int size() {
		return size;
	}

	public int first() {
		return nextTrue(0);
	}

	public int last() {
		return max;
	}

	public MyBitSet addAll(MyBitSet ibs) {
		if (ibs.max < 0) return this;
		// 100% true ...
		expand(ibs.max);
		max = Math.max(max, ibs.max);
		int ml = (ibs.max >>> 6) + 1;
		int ds = ibs.size;
		for (int i = 0; i < ml; i++) {
			long s = ibs.set[i], os = set[i] & s;

			while (os != 0) {
				if ((os & 1) != 0) { // both have
					ds--;
				}
				os >>>= 1;
			}

			set[i] |= s;
		}

		size += ds;
		return this;
	}

	public MyBitSet addAll(CharSequence s) {
		for (int i = 0; i < s.length(); i++) {
			add(s.charAt(i));
		}
		return this;
	}

	public boolean add(int e) {
		if (e < 0) throw new IllegalArgumentException();
		if (e > max) max = e;
		expand(e);
		long v = set[e >>> 6];
		if (v != (v = v | 1L << (e & 63))) {
			set[e >>> 6] = v;
			size++;
			return true;
		}
		return false;
	}

	public boolean remove(int e) {
		if (e < 0 || (e >>> 6) >= set.length) return false;
		//expand(e);
		if ((set[e >>> 6] & 1L << (e & 63)) != 0) {
			set[e >>> 6] &= ~(1L << (e & 63));
			if (e == max) max = prevTrue(max-1);
			size--;
			return true;
		}
		return false;
	}

	public void set(int i, boolean b) {
		if (b) add(i);
		else remove(i);
	}

	public int nextTrue(int pos) {
		if (pos < 0) pos = 0;

		int i = pos >>> 6;
		int max = (this.max+64)>>>6;
		if (i >= max) return -1;

		long v = set[i] & (-1L << pos);
		while (true) {
			if (v != 0) return (i<<6) + Long.numberOfTrailingZeros(v);
			if (++i == max) return -1;
			v = set[i];
		}
	}

	public int nextFalse(int pos) {
		if (pos < 0) pos = 0;

		int i = pos >>> 6;
		int max = (this.max+64)>>>6;
		if (i >= max) return pos;

		long v = ~set[i] & (-1L << pos);
		while (true) {
			if (v != 0) return (i<<6) + Long.numberOfTrailingZeros(v);
			if (++i == max) return this.max+1;
			v = ~set[i];
		}
	}

	/**
	 * @return 第n个真bit
	 */
	public int nthTrue(int n) {
		if (n < 0 || n > max) return -1;

		int i = 0;
		for (long k : set) {
			while (k != 0) {
				if ((k & 1) != 0) {
					if (n-- == 0) return i;
				}

				i++;
				k >>>= 1;
			}
		}

		return -1;
	}

	public int addRange(int from, int to) {
		if (from < 0 || to < 0) throw new IllegalArgumentException("from/to="+from+"/"+to);
		expand(to);
		to--;
		if (to > max) max = to;
		return setRange(from, to, false);
	}

	public int removeRange(int from, int to) {
		if (from < 0 || to < 0) throw new IllegalArgumentException("from/to="+from+"/"+to);
		to = Math.min(to-1, max);
		int i = -setRange(from, to, true);
		if (to == max) max = prevTrue(max-1);
		return i;
	}

	// both inclusive
	private int setRange(int from, int to, boolean clr) {
		int s = size;

		int i = from>>>6;
		long v = set[i];
		while (from <= to) {
			long x = 1L << (from & 63);
			if ((v & x) != 0 == clr) {
				v ^= x;
				size += clr?-1:1;
			}

			if ((++from & 63) == 0) {
				set[i] = v;
				v = set[++i];
			}
		}
		set[i] = v;

		return size - s;
	}

	public int prevTrue(int pos) {
		if (pos < 0) return -1;

		int i = pos >>> 6;
		int max = (this.max+64)>>>6;
		if (i >= max) return this.max;

		long v = set[i] & (-1L >>> (63-pos));
		while (true) {
			if (v != 0) return (i+1) * 64 -1 - Long.numberOfLeadingZeros(v);
			if (i-- == 0) return -1;
			v = set[i];
		}
	}

	public int prevFalse(int pos) {
		if (pos < 0) return -1;

		int i = pos >>> 6;
		int max = (this.max+64)>>>6;
		if (i >= max) return pos;

		long v = ~set[i] & (-1L >>> (63-pos));
		while (true) {
			if (v != 0) return (i+1) * 64 -1 - Long.numberOfLeadingZeros(v);
			if (i-- == 0) return -1;
			v = ~set[i];
		}
	}

	@Nonnull
	public IntIterator iterator() {
		return new FItr();
	}

	public void fill(int len) {
		expand(len);

		int x = len >> 6;
		for (int i = 0; i < x; i++) set[i] = -1L;
		set[x] = -1L >>> (64-(len&63));

		size = len--;
		max = len;
	}

	public MyBitSet copy() {
		MyBitSet copied = new MyBitSet(cap);
		System.arraycopy(set, 0, copied.set, 0, set.length);
		copied.max = this.max;
		copied.size = this.size;
		return copied;
	}

	public void clear() {
		Arrays.fill(set, 0);
		max = -1;
		size = 0;
	}

	@Override
	public String toString() {
		if (max < 0) return "{}";

		char[] str = new char[max+3];
		str[0] = '{';
		int aa = 1;

		int len = (max+64) >>> 6;
		long mask = (1L << ((max+1)&63)) - 1;
		while (len-- > 0) {
			long v = set[len];

			while (mask != 0) {
				str[aa++] = (v&mask) == 0 ? '0' : '1';
				mask >>>= 1;
			}
			mask = Long.MAX_VALUE;
		}

		str[aa] = '}';
		return new String(str);
	}

	public class FItr implements IntIterator {
		int stage = INITIAL;

		public final boolean hasNext() {
			check();
			return stage != ENDED;
		}
		public final int nextInt() {
			check();
			if (stage == ENDED) throw new NoSuchElementException();
			stage = GOTTEN;
			return pos;
		}
		public final void remove() {
			if (stage != GOTTEN) throw new IllegalStateException();
			stage = INITIAL;

			int t = pos;
			check();
			MyBitSet.this.remove(t);
		}
		public final void reset() {
			pos = -1;
			one = 0;
			stage = INITIAL;
		}
		public FItr() { reset(); }

		private void check() {
			if (stage <= GOTTEN) stage = computeNext() ? CHECKED : ENDED;
		}

		int pos;
		long one;

		private boolean computeNext() {
			long v = one;
			while (true) {
				if (v == 0) {
					pos = (pos|63)+1;

					while (true) {
						if (pos > max) return false;

						v = set[pos >>> 6];
						if (v != 0) break;
						pos += 64;
					}
				} else pos++;

				if ((v&1) != 0) {
					one = v >>> 1;
					return true;
				}
				v >>>= 1;
			}
		}
	}

	public long[] array() {
		return set;
	}
}