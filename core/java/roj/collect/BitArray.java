package roj.collect;

import org.jetbrains.annotations.Range;
import roj.compiler.runtime.RtUtil;
import roj.util.ArrayCache;

import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;

/**
 * int大概能快点？
 * @author Roj234
 * @since 2023/10/3 12:14
 */
public class BitArray {
	private final int[] data;
	private final int bits, mask, length;

	public BitArray(@Range(from = 1, to = 32) int bits, @Range(from = 0, to = Integer.MAX_VALUE) int length) {
		if (bits < 1 || bits > 32 || length < 0) throw new IllegalArgumentException();

		this.data = new int[(bits*length + 31) / 32];
		this.bits = bits;
		this.length = length;
		this.mask = bits == 32 ? -1 : ((1 << bits)-1);
	}
	public BitArray(@Range(from = 1, to = 32) int bits, @Range(from = 0, to = Integer.MAX_VALUE) int length, int[] data) {
		if (bits < 1 || bits > 32 || length < 0) throw new IllegalArgumentException();

		int len = (bits*length + 31) / 32;
		if (data.length != len) throw new IllegalArgumentException("data.length != "+len+" (is "+data.length+")");

		this.data = data;
		this.bits = bits;
		this.length = length;
		this.mask = bits == 32 ? -1 : ((1 << bits)-1);
	}

	public int bits() { return bits; }
	public int length() { return length; }

	public final int get(int i) {
		check(i);

		i *= bits;
		int bitPos = (i&31);
		i >>>= 5;

		if (bitPos+bits > 32) return (int) (((data[i]&0xFFFFFFFFL) | ((long) data[i+1] << 32)) >>> bitPos) & mask;
		else return (data[i] >>> bitPos) & mask;
	}
	public final void set(int i, int val) {
		check(i);
		if ((val & ~mask) != 0) throw new IllegalArgumentException("val "+val+" outside of mask "+mask);

		i *= bits;
		int bitPos = i&31;
		i >>>= 5;

		if (bitPos+bits > 32) {
			long myMask = (long) mask << bitPos;
			long longVal = ((long) val << bitPos) & myMask;

			long d = (data[i]&0xFFFFFFFFL) | ((long) data[i+1] << 32);
			d = d & ~myMask | longVal;
			data[i] = (int) d;
			data[i+1] = (int) (d >>> 32);
		} else {
			int myMask = mask << bitPos;
			val = (val << bitPos) & myMask;

			data[i] = data[i] & ~myMask | val;
		}
	}
	public void replace(int i, IntUnaryOperator op) {
		check(i);

		i *= bits;
		int bitPos = i&31;
		i >>>= 5;

		if (bitPos+bits > 32) {
			long myMask = (long) mask << bitPos;
			long d = (data[i]&0xFFFFFFFFL) | ((long) data[i+1] << 32);
			long longVal = ((long) op.applyAsInt((int) ((d & myMask) >>> bitPos)) << bitPos) & myMask;
			d = d & ~myMask | longVal;
			data[i] = (int) d;
			data[i+1] = (int) (d >>> 32);
		} else {
			int myMask = mask << bitPos;
			int val = (op.applyAsInt((data[i] & myMask) >>> bitPos) << bitPos) & myMask;
			data[i] = data[i] & ~myMask | val;
		}
	}

	public final void setAll(int off, int len, int val) {
		checkBatch(off, len);
		if ((val & ~mask) != 0) throw new IllegalArgumentException("val "+val+" outside of mask "+mask);
		if (len == 0) return;

		int i = off*bits;

		int bitPos = i&31;
		i >>>= 5;
		long d = data[i]&0xFFFFFFFFL;
		if (data.length > 1)
			d |= (long) data[++i] << 32;

		val &= mask;
		while (true) {
			long myMask = (long) mask << bitPos;
			long longVal = (long) val << bitPos;

			d = d & ~myMask | longVal;

			if (--len == 0) break;

			if ((bitPos += bits) > 32) {
				bitPos -= 32;
				data[i] = (int) d;
				d = ((++i == data.length) ? 0L : (long) data[i] << 32) | d >>> 32;
			}
		}

		if (i < data.length) data[i] = (int) d;
	}
	public final void getAll(int off, int len, IntConsumer consumer) {
		checkBatch(off, len);
		if (len == 0) return;

		int i = off*bits;

		int bitPos = i&31;
		i >>>= 5;
		long d = data[i]&0xFFFFFFFFL;
		if (i+1 < data.length)
			d |= (long) data[++i] << 32;

		while (true) {
			consumer.accept((int) (d >>> bitPos & mask));

			if (--len == 0) break;

			if ((bitPos += bits) > 32) {
				bitPos -= 32;
				d = ((++i == data.length) ? 0L : (long) data[i] << 32) | d >>> 32;
			}
		}
	}

	public final void putAll(BitArray array) { putAll(array, 0, array.length, false); }
	public final void putAll(BitArray array, int off, int len) { putAll(array, off, len, false); }
	public final void putAll(BitArray array, int off, int len, boolean cast) {
		if (array.bits > bits && !cast) throw new IllegalArgumentException("array.bits > bits: truncation");

		checkBatch(off, len);
		array.checkBatch(off, len);

		copyInternal(array.data, array.bits, data, bits, off, len);
	}

	private static void copyInternal(int[] from, int fromBits, int[] to, int toBits, int off, int len) {
		int i = off*fromBits, bitPos = i&31;
		i >>>= 5;
		long d = from[i]&0xFFFFFFFFL;
		if (i+1 < from.length)
			d |= (long) from[++i] << 32;

		int i2 = off*toBits, bitPos2 = i2&31;
		i2 >>>= 5;
		long d2 = to[i2]&0xFFFFFFFFL;

		long mask = (1L << fromBits)-1;
		long mask2 = (1L << toBits)-1;

		while (true) {
			long myMask = mask << bitPos2;
			long longVal = (((d >>> bitPos) & mask2) << bitPos2) & myMask;

			d2 = d2 & ~myMask | longVal;

			if (--len == 0) break;

			if ((bitPos += fromBits) > 32) {
				bitPos -= 32;
				d = ((++i == from.length) ? 0L : (long) from[i] << 32) | d >>> 32;
			}

			if ((bitPos2 += toBits) > 32) {
				bitPos2 -= 32;
				to[i2] = (int) d2;
				d2 = ((++i2 == to.length) ? 0L : (long) to[i2] << 32) | d2 >>> 32;
			}
		}

		if (i2 < to.length) to[i2] = (int) d2;
	}

	public int[] getInternal() { return data; }
	public int[] toIntArray() {
		if (length == 0) return ArrayCache.INTS;
		int[] out = new int[length];

		int i = 0, bitPos = 0;
		long d = data[i]&0xFFFFFFFFL;
		if (data.length > 1)
			d |= (long) data[++i] << 32;

		int j = 0;
		while (true) {
			out[j++] = (int) (d >>> bitPos & mask);

			if (j == length) return out;

			if ((bitPos += bits) > 32) {
				bitPos -= 32;
				d = ((++i == data.length) ? 0L : (long) data[i] << 32) | d >>> 32;
			}
		}
	}

	public String pack() {return RtUtil.pack(data);}

	private void check(int i) {
		if (i < 0 || i >= length) throw new ArrayIndexOutOfBoundsException(i);
	}
	private void checkBatch(int off, int len) {
		if ((off|len) < 0 || off+len < 0 || off+len > length)
			throw new ArrayIndexOutOfBoundsException("off="+off+",len="+len+",arraylen="+length);
	}

	@Override
	public String toString() { return "BitArray("+bits+")["+length+"]"+Arrays.toString(toIntArray()); }
}