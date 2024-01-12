package roj.util;

import roj.config.word.ITokenizer;
import roj.io.IOUtil;
import roj.reflect.DirectAccessor;
import roj.text.CharList;
import roj.ui.CLIUtil;

import java.awt.*;
import java.awt.datatransfer.*;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.ToIntFunction;

import static roj.reflect.ReflectionUtils.BIG_ENDIAN;
import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2020/10/15 0:43
 */
public final class ArrayUtil {
	interface H {
		int vectorizedMismatch(Object a, long aOffset,
							   Object b, long bOffset,
							   int length,
							   int log2ArrayIndexScale);
	}
	private static H fastCompare;
	static {
		try {
			fastCompare = DirectAccessor.builder(H.class).inline().delegate(Class.forName("jdk.internal.util.ArraysSupport"), "vectorizedMismatch").build();
		} catch (Throwable ignored) {}
	}

	public static void pack(int[] arr) {
		ByteList tmp = IOUtil.getSharedByteBuf();
		for (int i = 0; i < arr.length; i++) tmp.putInt(arr[i]);
		Base128(tmp);
	}
	public static void pack(byte[] arr) { Base128(ByteList.wrap(arr)); }

	private static void Base128(ByteList tmp) {
		BitBuffer br = new BitBuffer(tmp);
		CharList sb = new CharList();
		sb.ensureCapacity(tmp.readableBytes() * 8/7 + 1);
		while (br.readableBits() >= 7) sb.append((char) (br.readBit(7)+1));
		if (br.readableBits() > 0) sb.append((char) (br.readBit(br.readableBits())+1));

		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(ITokenizer.addSlashes(sb, 0, new CharList().append('"'), '\'').append('"').toStringAndFree()), null);
		CLIUtil.pause();
	}

	private static ByteList UnBase128(String s) {
		int len = s.length() * 7/8;

		ByteList tmp = ByteList.allocate(len,len);
		BitBuffer br = new BitBuffer(tmp);
		for (int i = 0; i < s.length()-1; i++) br.writeBit(7, s.charAt(i)-1);

		br.writeBit(8-br.bitPos, s.charAt(s.length()-1)-1);
		br.endBitWrite();

		return tmp;
	}

	public static byte[] unpackB(String s) {
		return UnBase128(s).list;
	}
	public static int[] unpackI(String s) {
		ByteList list = UnBase128(s);
		int[] b = new int[list.readableBytes()>>>2];
		for (int i = 0; i < b.length; i++) b[i] = list.readInt();
		return b;
	}

	public static <T> List<T> inverse(List<T> list) { return inverse(list, 0, list.size()); }
	public static <T> List<T> inverse(List<T> list, int i, int length) {
		if (--length <= 0) return list;

		for (int end = Math.max((length + 1) >> 1, 1); i < end; i++) {
			T a = list.set(i, list.get(length-i));
			list.set(length-i, a);
		}
		return list;
	}

	public static <T> void shuffle(List<T> list, Random r) {
		for (int i = 0; i < list.size(); i++) {
			int an = r.nextInt(list.size());
			T a = list.set(i, list.get(an));
			list.set(an, a);
		}
	}

	public static boolean rangedEquals(byte[] b, int off1, int len1, byte[] b1, int off2, int len2) {
		if (len1 != len2) return false;
		while (len1-- > 0) {
			if (b[off1++] != b1[off2++]) return false;
		}
		return true;
	}

	public static final int LOG2_ARRAY_BOOLEAN_INDEX_SCALE = 0;
	public static final int LOG2_ARRAY_BYTE_INDEX_SCALE = 0;
	public static final int LOG2_ARRAY_CHAR_INDEX_SCALE = 1;
	public static final int LOG2_ARRAY_SHORT_INDEX_SCALE = 1;
	public static final int LOG2_ARRAY_INT_INDEX_SCALE = 2;
	public static final int LOG2_ARRAY_LONG_INDEX_SCALE = 3;
	public static final int LOG2_ARRAY_FLOAT_INDEX_SCALE = 2;
	public static final int LOG2_ARRAY_DOUBLE_INDEX_SCALE = 3;
	private static final int LOG2_BYTE_BIT_SIZE = 3;

	public static int vectorizedMismatch(Object a, long aOffset,
										 Object b, long bOffset,
										 int length,
										 int log2ArrayIndexScale) {
		if (fastCompare != null) return fastCompare.vectorizedMismatch(a, aOffset, b, bOffset, length, log2ArrayIndexScale);
		// 当null时也就意味着是Java8 ...

		int log2ValuesPerWidth = LOG2_ARRAY_LONG_INDEX_SCALE - log2ArrayIndexScale;
		int wi = 0;
		for (; wi < length >> log2ValuesPerWidth; wi++) {
			long bi = ((long) wi) << LOG2_ARRAY_LONG_INDEX_SCALE;
			long av = u.getLong(a, aOffset + bi);
			long bv = u.getLong(b, bOffset + bi);
			if (av != bv) {
				long x = av ^ bv;
				int o = BIG_ENDIAN
					? Long.numberOfLeadingZeros(x) >> (LOG2_BYTE_BIT_SIZE + log2ArrayIndexScale)
					: Long.numberOfTrailingZeros(x) >> (LOG2_BYTE_BIT_SIZE + log2ArrayIndexScale);
				return (wi << log2ValuesPerWidth) + o;
			}
		}

		// Calculate the tail of remaining elements to check
		int tail = length - (wi << log2ValuesPerWidth);

		if (log2ArrayIndexScale < LOG2_ARRAY_INT_INDEX_SCALE) {
			int wordTail = 1 << (LOG2_ARRAY_INT_INDEX_SCALE - log2ArrayIndexScale);
			// Handle 4 bytes or 2 chars in the tail using int width
			if (tail >= wordTail) {
				long bi = ((long) wi) << LOG2_ARRAY_LONG_INDEX_SCALE;
				int av = u.getInt(a, aOffset + bi);
				int bv = u.getInt(b, bOffset + bi);
				if (av != bv) {
					int x = av ^ bv;
					int o = BIG_ENDIAN
						? Integer.numberOfLeadingZeros(x) >> (LOG2_BYTE_BIT_SIZE + log2ArrayIndexScale)
						: Integer.numberOfTrailingZeros(x) >> (LOG2_BYTE_BIT_SIZE + log2ArrayIndexScale);
					return (wi << log2ValuesPerWidth) + o;
				}
				tail -= wordTail;
			}
			return ~tail;
		}
		else {
			return ~tail;
		}
	}

	public static int binarySearch(Object[] a, int low, int high, Object key, Comparator<Object> cmp) {
		high--;

		while (low <= high) {
			int mid = (low + high) >>> 1;
			int midVal = cmp.compare(a[mid], key);

			if (midVal < 0) {
				low = mid + 1;
			} else if (midVal > 0) {
				high = mid - 1;
			} else {
				return mid; // key found
			}
		}

		// low ...

		return -(low + 1);  // key not found.
	}

	public static String toString(Object[] list, int i, int length) {
		if (length - i <= 0) return "[]";
		StringBuilder sb = new StringBuilder().append('[');

		for (; i < length; i++) {
			sb.append(list[i]).append(", ");
		}
		sb.delete(sb.length() - 2, sb.length());
		return sb.append(']').toString();
	}

	public static int byteHashCode(Object o, long off, int len) {
		if (len == 0) return 0;

		long end = off+len;
		int hash = 1;
		while (off < end) {
			hash = u.getByte(o, off++) + 31 * hash;
		}
		return hash;
	}

	public static <T> int binarySearchEx(List<T> list, ToIntFunction<T> comparator) { return binarySearchEx(list, 0, list.size(), comparator); }
	public static <T> int binarySearchEx(List<T> list, int low, int high, ToIntFunction<T> comparator) {
		high--;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			T midVal = list.get(mid);
			int cmp = comparator.applyAsInt(midVal);
			if (cmp < 0) low = mid + 1;
			else if (cmp > 0) high = mid - 1;
			else return mid;
		}
		return -(low + 1);
	}

	public static void checkRange(byte[] b, int off, int len) {
		if ((off|len|(off+len)) < 0 || off + len > b.length)
			throw new IndexOutOfBoundsException("off="+off+",len="+len+",cap="+b.length);
	}
}