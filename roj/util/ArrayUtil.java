package roj.util;

import roj.config.word.ITokenizer;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.ui.CLIUtil;

import java.awt.*;
import java.awt.datatransfer.*;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.ToIntFunction;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2020/10/15 0:43
 */
public final class ArrayUtil {
	public static void pack(int[] arr) {
		ByteList tmp = IOUtil.getSharedByteBuf();
		for (int i = 0; i < arr.length; i++) tmp.putInt(arr[i]);
		Base128(tmp);
	}
	public static void pack(byte[] arr) { Base128(ByteList.wrap(arr)); }

	private static void Base128(ByteList tmp) {
		BitWriter br = new BitWriter(tmp);
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
		BitWriter br = new BitWriter(tmp);
		for (int i = 0; i < s.length()-1; i++) br.writeBit(7, s.charAt(i)-1);

		br.writeBit(8-br.bitIndex, s.charAt(s.length()-1)-1);
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

	public static <T> T[] inverse(T[] arr) {
		return inverse(arr, 0, arr.length);
	}

	public static <T> T[] inverse(T[] arr, int i, int length) {
		if (--length <= 0) return arr;

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

	public static void shuffleArray(Object arr, long off, int len, int scale, Random random) {
		long ptr = u.allocateMemory(scale);
		if (ptr == 0) throw new OutOfMemoryError();

		len *= scale;
		try {
			for (int i = 0; i < len; i += scale) {
				u.copyMemory(arr, off+i, null, ptr, scale);
				int j = random.nextInt(len)*scale;
				u.copyMemory(arr, off+j, arr, off+i, scale);
				u.copyMemory(null, ptr, arr, off+j, scale);
			}
		} finally {
			u.freeMemory(ptr);
		}
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
		while (len1-- > 0) {
			if (b[off1++] != b1[off2++]) return false;
		}
		return true;
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

	public static int rangedHashCode(byte[] b, int off, int len) {
		if (len == 0) return 0;

		len += off;
		int hash = 1;
		while (off < len) {
			hash = b[off++] + 31 * hash;
		}
		return hash;
	}

	public static <T extends Comparable<T>> int binarySearchList(List<T> list, T key) { return binarySearchList(list, 0, list.size(), key); }
	public static <T extends Comparable<T>> int binarySearchList(List<T> list, int low, int high, T key) { return binarySearchEx(list, low, high, key::compareTo); }
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
