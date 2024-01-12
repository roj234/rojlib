package roj.collect;

import org.jetbrains.annotations.Range;
import roj.util.ArrayCache;
import roj.util.ArrayUtil;

import java.util.Arrays;
import java.util.function.IntConsumer;

/**
 * int大概能快点？
 * @author Roj234
 * @since 2023/10/3 0003 12:14
 */
public class BitArray {
	private final int[] data;
	private final int bits, mask, length;

	public BitArray(String unpack) {
		data = ArrayUtil.unpackI(unpack);
		bits = data[data.length-1];
		length = (data.length-1) * 32 / bits;
		mask = ((1 << bits)-1);
	}

	public BitArray(@Range(from = 1, to = 32) int bits, @Range(from = 0, to = Integer.MAX_VALUE) int length) {
		if (bits < 1 || bits > 32 || length < 0) throw new IllegalArgumentException();

		this.data = new int[(bits*length + 31) / 32];
		this.bits = bits;
		this.length = length;
		this.mask = ((1 << bits)-1);
	}
	public BitArray(int bits, int length, int[] data) {
		int len = (bits*length + 31) / 32;
		if (data.length != len) throw new IllegalArgumentException("data.length != "+len+" (is "+data.length+")");

		this.data = data;
		this.bits = bits;
		this.length = length;
		this.mask = ((1 << bits)-1);
	}

	public int length() { return length; }

	public final int get(int i) {
		check(i);

		i *= bits;
		int bitPos = (i&31);
		i >>>= 5;

		if (bitPos+bits > 31) return (int) (((data[i]&0xFFFFFFFFL) | ((long) data[i+1] << 32)) >>> bitPos) & mask;
		else return (data[i] >>> bitPos) & mask;
	}
	public final void set(int i, int val) {
		i&=0xff;
		check(i);

		i *= bits;
		int bitPos = i&31;
		i >>>= 5;

		if (bitPos+bits > 31) {
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

	public final void setAll(int off, int len, int val) {
		checkBatch(off, len);
		if (len == 0) return;

		int i = off*bits;

		int bitPos = i&31;
		i >>>= 5;
		long d = data[i]&0xFFFFFFFFL;
		if (i+1 < data.length)
			d |= (long) data[i+1] << 32;

		val &= mask;
		while (true) {
			long myMask = (long) mask << bitPos;
			long longVal = (long) val << bitPos;

			d = d & ~myMask | longVal;

			if (--len == 0) break;

			if ((bitPos += bits) > 31) {
				bitPos -= 32;

				data[i] = (int) d;

				d = d>>>32 | (long)data[i+2] << 32;

				i++;
			}
		}

		data[i] = (int) d;
	}
	public final void getAll(int off, int len, IntConsumer consumer) {
		checkBatch(off, len);
		if (len == 0) return;

		int i = off*bits;

		int bitPos = i&31;
		i >>>= 5;
		long d = data[i]&0xFFFFFFFFL;
		if (i+1 < data.length)
			d |= (long) data[i+1] << 32;

		while (true) {
			consumer.accept((int) (d >>> bitPos & mask));

			if (--len == 0) break;

			if ((bitPos += bits) > 31) {
				bitPos -= 32;

				d = d>>>32 | (long)data[i+2] << 32;

				i++;
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
		long d = from[i]&0xFFFFFFFFL;
		if (i+1 < from.length)
			d |= (long) from[i+1] << 32;
		i >>>= 5;

		int i2 = off*toBits, bitPos2 = i2&31;
		i2 >>>= 5;
		long d2 = to[i2]&0xFFFFFFFFL;
		if (i2+1 < to.length)
			d2 |= (long) to[i2+1] << 32;

		long mask = (1 << toBits)-1;

		while (true) {
			long myMask = mask << bitPos2;
			long longVal = ((d >>> bitPos) << bitPos2) & myMask;

			d2 = d2 & ~myMask | longVal;

			if (--len == 0) break;

			if ((bitPos += fromBits) > 31) {
				bitPos -= 32;
				d = d>>>32 | (long) from[i+2] << 32;
				i++;
			}

			if ((bitPos2 += toBits) > 31) {
				bitPos2 -= 32;
				to[i2] = (int) d2;
				d2 = d2>>>32 | (long)to[i2+2] << 32;
				i2++;
			}
		}

		to[i2] = (int) d2;
	}

	public int[] getInternal() { return data; }
	public int[] toIntArray() {
		if (length == 0) return ArrayCache.INTS;
		int[] out = new int[length];

		int i = 0, bitPos = 0;
		long d = data[i]&0xFFFFFFFFL;
		if (i+1 < data.length)
			d |= (long) data[i+1] << 32;

		int j = 0;
		while (true) {
			out[j++] = (int) (d >>> bitPos & mask);

			if (j == length) return out;

			if ((bitPos += bits) > 31) {
				bitPos -= 32;

				d = d>>>32 | (long)data[i+2] << 32;

				i++;
			}
		}
	}

	public void pack() {
		int[] data1 = Arrays.copyOf(data, data.length+1);
		data1[data1.length-1] = bits;
		ArrayUtil.pack(data1);
	}

	private void check(int i) {
		if (i < 0 || i >= length) throw new ArrayIndexOutOfBoundsException(i);
	}
	private void checkBatch(int off, int len) {
		if ((off|len) < 0 || off+len < 0 || off+len > length)
			throw new ArrayIndexOutOfBoundsException("off="+off+",len="+len+",arraylen="+length);
	}

	@Override
	public String toString() { return Arrays.toString(toIntArray()); }
}