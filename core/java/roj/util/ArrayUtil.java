package roj.util;

import roj.compiler.plugins.annotations.Attach;
import roj.compiler.plugins.asm.ASM;
import roj.reflect.Bypass;

import java.util.*;
import java.util.function.ToIntFunction;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2020/10/15 0:43
 */
public final class ArrayUtil {
	interface H {
		H SCOPED_MEMORY_ACCESS = init();
		private static H init() {
			try {
				return Bypass.builder(H.class).inline().delegate(Class.forName("jdk.internal.util.ArraysSupport"), "vectorizedMismatch").build();
			} catch (Throwable ignored) {}
			return null;
		}

		int vectorizedMismatch(Object a, long aOffset,
							   Object b, long bOffset,
							   int length,
							   int log2ArrayIndexScale);
	}

	@Attach
	public static <T> List<T> inverse(List<T> list) { return inverse(list, 0, list.size()); }
	@Attach
	public static <T> List<T> inverse(List<T> list, int i, int length) {
		if (--length <= 0) return list;

		for (int end = Math.max((length + 1) >> 1, 1); i < end; i++) {
			T a = list.set(i, list.get(length-i));
			list.set(length-i, a);
		}
		return list;
	}
	@Attach
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

	public static int compare(Object a, long aOffset,
							  Object b, long bOffset,
							  int length,
							  int log2ArrayIndexScale) {
		int i = 0;
		// 当null时也就意味着是Java8 ... 无法确定处理器是否支持不对齐访问呢
		if (length > (8 >> log2ArrayIndexScale) - 1 && H.SCOPED_MEMORY_ACCESS != null) {
			if (U.getByte(a, aOffset) != U.getByte(b, bOffset))
				return 0;
			i = H.SCOPED_MEMORY_ACCESS.vectorizedMismatch(a, aOffset, b, bOffset, length, log2ArrayIndexScale);
			if (i >= 0) return i;
			i = length - ~i;
		}

		for (; i < length; i++) {
			if (U.getByte(a, aOffset+i) != U.getByte(b, bOffset+i))
				return i;
		}

		return -1;
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
			hash = U.getByte(o, off++) + 31 * hash;
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

	public static void checkRange(byte[] b, int off, int len) {checkRange(b.length, off, len);}
	public static void checkRange(int capacity, int off, int len) {
		if ((off|len|(off+len)) < 0 || off + len > capacity)
			throw new IndexOutOfBoundsException("off="+off+",len="+len+",cap="+capacity);
	}


	private static final Class<?> IMMUTABLE_ARRAY_TYPE = Arrays.asList().getClass();
	@SuppressWarnings("unchecked")
	public static <T> List<T> immutableCopyOf(Collection<T> list) {
		if (list.isEmpty()) return Collections.emptyList();
		if (list.getClass() == IMMUTABLE_ARRAY_TYPE) return (List<T>) list;
		if (ASM.TARGET_JAVA_VERSION > 9) {
			return List.copyOf(list);
		} else {
			return (List<T>) Arrays.asList(list.toArray());
		}
	}
}