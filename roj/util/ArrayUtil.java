package roj.util;

import roj.config.word.ITokenizer;
import roj.io.IOUtil;
import roj.text.CharList;

import java.util.List;
import java.util.Random;

import static roj.reflect.FieldAccessor.u;

/**
 * @author Roj234
 * @since 2020/10/15 0:43
 */
public final class ArrayUtil {
	public static String pack(int[] arr) {
		ByteList tmp = IOUtil.getSharedByteBuf();
		for (int i = 0; i < arr.length; i++) tmp.putInt(arr[i]);
		return Base128(tmp);
	}
	public static String pack(byte[] arr) { return Base128(ByteList.wrap(arr)); }

	private static String Base128(ByteList tmp) {
		BitWriter br = new BitWriter(tmp);
		CharList sb = IOUtil.getSharedCharBuf();
		sb.ensureCapacity(tmp.readableBytes() * 8/7 + 1);
		while (br.readableBits() >= 7) sb.append((char) (br.readBit(7)+1));
		if (br.readableBits() > 0) sb.append((char) (br.readBit(br.readableBits())+1));
		return ITokenizer.addSlashes(sb);
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
