package roj.collect;

import org.jetbrains.annotations.NotNull;
import roj.io.MyDataInput;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.Arrays;
import java.util.NoSuchElementException;

import static roj.collect.AbstractIterator.*;

/**
 * @author Roj234
 * @since 2021/5/11 23:9
 */
public class MyBitSet implements Iterable<Integer> {
	private long[] set;
	private int cap;
	private int max = -1;
	private int size;

	public MyBitSet() { this(1); }
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

	public final boolean contains(int key) {
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

	public final int size() { return size; }
	public final int first() { return nextTrue(0); }
	public final int last() { return max; }

	public final MyBitSet and(MyBitSet o) {
		if (o.max < 0) {
			clear();
			return this;
		}

		max = Math.min(max, o.max);

		int myLen = (max >>> 6) + 1;
		int len = (o.max >>> 6) + 1;

		int addSize = o.size;

		for (int i = 0; i < len; i++) {
			long num = o.set[i]&set[i];
			addSize += Long.bitCount(num);
			set[i] = num;
		}
		for (int i = len; i < myLen; i++) {
			set[i] = 0;
		}

		size = addSize;
		return this;
	}
	public final MyBitSet or(MyBitSet o) {
		if (o.max < 0) return this;

		expand(o.max);
		max = Math.max(max, o.max);

		int len = (o.max >>> 6) + 1;
		int addSize = 0;

		for (int i = 0; i < len; i++) {
			long num = o.set[i]|set[i];
			addSize += Long.bitCount(num);
			set[i] = num;
		}
		for (int i = len; i < set.length; i++) {
			addSize += Long.bitCount(set[i]);
		}

		size = addSize;
		return this;
	}
	public final MyBitSet xor(MyBitSet o) {
		if (o.max < 0) return this;

		expand(o.max);
		int theMax = Math.max(max, o.max);

		int len = (o.max >>> 6) + 1;
		int addSize = 0;

		for (int i = 0; i < len; i++) {
			long num = o.set[i]^set[i];
			addSize += Long.bitCount(num);
			set[i] = num;
		}
		for (int i = len; i < set.length; i++) {
			addSize += Long.bitCount(set[i]);
		}

		int max1 = max+1;
		while (true) {
			int next = trueRange(max1, cap>>>6);
			if (next < 0) break;
			max1 = next+1;
		}

		max = prevTrue(max = max1);
		size = addSize;
		return this;
	}

	public final MyBitSet addAll(CharSequence s) {
		for (int i = 0; i < s.length(); i++) add(s.charAt(i));
		return this;
	}

	public final boolean add(int e) {
		if (e < 0) throw new IllegalArgumentException();
		if (e > max) max = e;
		expand(e);
		long v = set[e >>> 6];
		if (v != (v |= 1L << (e & 63))) {
			set[e >>> 6] = v;
			size++;
			return true;
		}
		return false;
	}

	public final boolean remove(int e) {
		if (e < 0 || (e >>> 6) >= set.length) return false;
		//expand(e);
		long mask = 1L << (e & 63);
		if ((set[e >>> 6] & mask) != 0) {
			set[e >>> 6] &= ~mask;
			if (e == max) max = prevTrue(max-1);
			size--;
			return true;
		}
		return false;
	}

	public final void set(int i, boolean b) {
		if (b) add(i);
		else remove(i);
	}

	public final boolean allFalse(int from, int to) {
		int pos = trueRange(from, (Math.min(max,to)+64>>>6));
		return pos >= to || pos < 0;
	}
	public final int nextTrue(int pos) {
		return trueRange(pos, (max+64)>>>6);
	}
	private int trueRange(int pos, int max) {
		if (pos < 0) pos = 0;

		int i = pos >>> 6;
		if (i >= max) return -1;

		long v = set[i] & (-1L << pos);
		while (true) {
			if (v != 0) return (i<<6) + Long.numberOfTrailingZeros(v);
			if (++i == max) return -1;
			v = set[i];
		}
	}

	public final boolean allTrue(int from, int to) {
		int pos = falseRange(from, (Math.min(max,to)+64>>>6));
		return pos >= to;
	}
	public final int nextFalse(int pos) {
		return falseRange(pos, (max+64)>>>6);
	}
	private int falseRange(int pos, int max) {
		if (pos < 0) pos = 0;

		int i = pos >>> 6;
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
	public final int nthTrue(int n) {
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

	public final int addRange(int from, int to) {
		if (from < 0 || to < 0) throw new IllegalArgumentException("from/to="+from+"/"+to);
		expand(to);
		to--;
		if (max < to) max = to;
		return setRange(from, to, false);
	}

	public final int removeRange(int from, int to) {
		if (from < 0 || to < 0) throw new IllegalArgumentException("from/to="+from+"/"+to);
		to = Math.min(to-1, max);
		int i = -setRange(from, to, true);
		if (to == max) max = prevTrue(max);
		return i;
	}

	public final int countRange(int from, int to) {
		if (from < 0 || to < 0) throw new IllegalArgumentException("from/to="+from+"/"+to);
		to = Math.min(to-1, max);

		int val = 0;

		int i = from>>>6;
		long v = set[i];
		while (from <= to) {
			long x = 1L << (from & 63);
			if ((v & x) != 0) {
				v ^= x;
				val++;
			}

			if ((++from & 63) == 0) v = set[++i];
		}

		return val;
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

	public final int prevTrue(int pos) {
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

	public final int prevFalse(int pos) {
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

	@NotNull
	public IntIterator iterator() {
		return new FItr();
	}

	public final void fill(int len) {
		expand(len);

		int x = len >> 6;
		for (int i = 0; i < x; i++) set[i] = -1L;
		set[x] = -1L >>> (64-(len&63));

		size = len--;
		max = len;
	}

	public final void shiftLeft(int i) {
		if (i < 0) {
			shiftRight(-i);
			return;
		}

		expand(max+i);
		int block = i>>6;
		int shift = i&63;
		int shift1 = 64-shift;

		for (int j = max>>>6; j > 0; j--) set[j+block] = set[j] << shift | set[j-1] >>> shift1;
		set[block] = set[0] << shift;
		while (block > 0) set[--block] = 0;

		max += i;
	}
	public final void shiftRight(int i) {
		if (i < 0) {
			shiftLeft(-i);
			return;
		}

		if (i > max) {
			clear();
			return;
		}

		int block = i>>6;
		int shift = i&63;
		int shift1 = 64-shift;

		int end = max >>> 6;
		if (shift1 == 64) {
			for (int j = block; j < end; j++) set[j-block] = set[j] >>> shift;
		} else {
			for (int j = block; j < end; j++) set[j-block] = set[j+1] << shift1 | set[j] >>> shift;
		}
		set[end-block] = set[end] >>> shift;
		for (int j = end; j > end-block; j--) set[j] = 0;

		max -= i;
	}

	public Throwable x;
	public final MyBitSet copy() {return copyTo(new MyBitSet(cap-1));}
	public final MyBitSet copyTo(MyBitSet copied) {
		copied.expand(cap);
		System.arraycopy(set, 0, copied.set, 0, set.length);
		for (int i = set.length; i < copied.set.length; i++) {
			copied.set[i] = 0;
		}
		copied.max = this.max;
		copied.size = this.size;
		return copied;
	}

	public final void clear() {
		Arrays.fill(set, 0);
		max = -1;
		size = 0;
	}

	@Override
	public String toString() {
		if (max < 0) return "{}";

		char[] str = new char[max+3];
		str[0] = '{';
		int j = 1;

		int end = max >>> 6;
		for (int i = 0; i < end; i++) {
			long v = set[i];
			long mask = 1;
			while (mask != 0) {
				str[j++] = (v&mask) == 0 ? '0' : '1';
				mask <<= 1;
			}
		}

		long v = set[end];
		long mask = 1;
		for (int i = max-end*64; i >= 0; i--) {
			str[j++] = (v&mask) == 0 ? '0' : '1';
			mask <<= 1;
		}

		str[j] = '}';
		return new String(str);
	}

	public static MyBitSet readBits(MyDataInput buf, int byteLength) throws IOException {
		if (byteLength == 0) return new MyBitSet();

		long[] set = new long[(byteLength+63)/64];
		int i = 0;
		int count = 0;

		while (byteLength >= 64) {
			long bits = buf.readLongLE();

			count += Long.bitCount(bits);
			set[i++] = invertBits(bits);

			byteLength -= 64;
		}

		int shl = 0;
		long fin = 0;
		while (byteLength > 0) {
			fin |= (long) buf.readUnsignedByte() << shl;
			shl += 8;
			byteLength -= 8;
		}

		count += Long.bitCount(fin);
		set[set.length-1] = invertBits(fin);

		return new MyBitSet(set, count);
	}
	public void writeBits(DynByteBuf buf) {
		int size = max+1;

		int i = 0;

		while (size >= 64) {
			buf.putLongLE(invertBits(set[i++]));
			size -= 64;
		}

		long fin = invertBits(set[i]);
		while (size > 0) {
			buf.put((byte) fin);
			fin >>>= 8;
			size -= 8;
		}
	}
	static long invertBits(long i) {
		// 反转每个byte中的每个bit
		i = (i & 0x5555555555555555L) << 1 | (i >>> 1) & 0x5555555555555555L;
		i = (i & 0x3333333333333333L) << 2 | (i >>> 2) & 0x3333333333333333L;
		i = (i & 0x0f0f0f0f0f0f0f0fL) << 4 | (i >>> 4) & 0x0f0f0f0f0f0f0f0fL;
		return i;
	}
	public int byteLength() { return (max+8) / 8; }

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

	public final long[] array() {
		return set;
	}

	public int[] toIntArray() {
		int[] data = new int[size];
		int i = 0;
		for (IntIterator itr = iterator(); itr.hasNext(); ) data[i++] = itr.nextInt();
		return data;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof MyBitSet mbs && mbs.size == this.size && mbs.max == this.max) {
			int size = max+1;
			int i = 0;

			while (size >= 64) {
				if (mbs.set[i] != set[i++]) return false;
				size -= 64;
			}
			if (size == 0) return true;

			long mask = (1L << size) - 1;
			return (mbs.set[i] & mask) == (set[i] & mask);
		}

		return false;
	}

	@Override
	public int hashCode() {
		int hash = 0;

		int size = max+1;
		int i = 0;

		while (size >= 64) {
			hash = hash*31 + Long.hashCode(set[i++]);
			size -= 64;
		}
		if (size == 0) return hash;

		long mask = (1L << size) - 1;
		return hash*31 + Long.hashCode(set[i]&mask);
	}
}