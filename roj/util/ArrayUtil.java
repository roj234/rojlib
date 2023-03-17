package roj.util;

import java.util.List;
import java.util.Random;

/**
 * @author Roj234
 * @since 2020/10/15 0:43
 */
public final class ArrayUtil {
	public static <T> T[] inverse(T[] arr) {
		return inverse(arr, 0, arr.length);
	}

	public static <T> T[] inverse(T[] arr, int i, int length) {
		if (--length <= 0) return arr; // empty or one
		// i = 0, arr.length = 4, e = 2
		// swap 0 and 3 swap 1 and 2
		for (int e = Math.max((length + 1) >> 1, 1); i < e; i++) {
			T a = arr[i];
			arr[i] = arr[length - i];
			arr[length - i] = a;
		}
		return arr;
	}

	public static <T> T[] inverse(T[] arr, int size) {
		return inverse(arr, 0, size);
	}

	public static <T> List<T> inverse(List<T> arr) {
		return inverse(arr, 0, arr.size());
	}

	public static <T> List<T> inverse(List<T> arr, int i, int length) {
		if (--length <= 0) return arr; // empty or one
		// i = 0, arr.length = 4, e = 2
		// swap 0 and 3 swap 1 and 2
		for (int e = Math.max((length + 1) >> 1, 1); i < e; i++) {
			T a = arr.get(i);
			arr.set(i, arr.get(length - i));
			arr.set(length - i, a);
		}
		return arr;
	}

	public static void shuffle(Object[] arr, Random random) {
		for (int i = 0; i < arr.length; i++) {
			Object a = arr[i];
			int an = random.nextInt(arr.length);
			arr[i] = arr[an];
			arr[an] = a;
		}
	}

	public static <T> void shuffle(List<T> arr, Random random) {
		for (int i = 0; i < arr.size(); i++) {
			T a = arr.get(i);
			int an = random.nextInt(arr.size());
			arr.set(i, arr.get(an));
			arr.set(an, a);
		}
	}

	public static boolean rangedEquals(byte[] b, int off1, int len1, byte[] b1, int off2, int len2) {
		if (len1 != len2) return false;
		len1 += off1;
		while (off1 < len1) {
			if (b[off1++] != b1[off2++]) return false;
		}
		return true;
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

	public static int rangedHashCode(byte[] b, int off, int len) {
		if (len == 0) return 0;

		len += off;
		int hash = 1;
		while (off < len) {
			hash = b[off++] + 31 * hash;
		}
		return hash;
	}
}
