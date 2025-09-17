package roj.util;

import roj.annotation.Status;
import roj.reflect.Unsafe;
import roj.text.CharList;
import roj.text.TextUtil;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

import static roj.reflect.Unsafe.U;

public class ArrayCache {
	public static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

	public static final byte[] BYTES = new byte[0];
	public static final char[] CHARS = new char[0];
	public static final int[] INTS = new int[0];
	public static final float[] FLOATS = new float[0];
	public static final double[] DOUBLES = new double[0];
	public static final long[] LONGS = new long[0];
	public static final Object[] OBJECTS = new Object[0];
	public static final Class<?>[] CLASSES = new Class<?>[0];
	public static <T> WeakReference<T> emptyWeakRef() {return Helpers.cast(G_Sential);}

	private static final int MIN_ARRAY_SIZE = 256, LARGE_ARRAY_SIZE = 1048576;
	private static final int ID_COUNT = 3, CACHE_COUNT = 15;

	private static final ThreadLocal<ArrayCache> CACHE = new ThreadLocal<>();
	private static ArrayCache small() {
		ArrayCache cache = CACHE.get();
		if (cache == null) CACHE.set(cache = new ArrayCache());
		return cache;
	}

	// 100KB左右的指针
	private static final Object[] G_Cache = new Object[ID_COUNT * 256 * CACHE_COUNT];
	private static final int[] G_Using = new int[ID_COUNT * 256];
	private static final WeakReference<?> G_Sential = new WeakReference<>(null);

	// 1<<8 (256) => 1<<20 (1048576)
	private final Object[] cache = new Object[ID_COUNT * 13 * CACHE_COUNT];
	private final int[] using = new int[ID_COUNT * 13];

	@Status
	public static CharList status(CharList sb) {
		sb.append("小数组缓存(Reserved/Total):\n");
		var ac = CACHE.get();
		if (ac == null) sb.append("未创建");
		else {
			int[] ints = ac.using;
			for (int i = 0; i < ints.length; i++) {
				int bitset = ints[i];
				int exist = 0, reserved = 0;
				var base = i * CACHE_COUNT;
				for (int j = 0; j < CACHE_COUNT; j++) {
					var r = (Reference<?>) ac.cache[base+j];
					if (r != null && r.get() != null) {
						exist++;
						if ((bitset&(1 << j)) != 0) reserved++;
					}
				}
				if ((exist|reserved) == 0) continue;

				sb.append("  类型").append(typeOf(i / 13)).append(" 容量");
				TextUtil.scaledNumber1024(sb, 1L << (8 + i % 13));
				sb.append(": ").append(reserved).append('/').append(exist).append('\n');
			}
		}

		sb.append("大数组缓存:\n");
		int[] ints = G_Using;
		for (int i = 0; i < ints.length; i++) {
			int bitset = ints[i];
			int exist = 0, reserved = 0;
			var base = i * CACHE_COUNT;
			for (int j = 0; j < CACHE_COUNT; j++) {
				var r = (Reference<?>) G_Cache[base+j];
				if (r != null && r.get() != null) {
					exist++;
					if ((bitset&(1 << j)) != 0) reserved++;
				}
			}
			if ((exist|reserved) == 0) continue;

			sb.append("  类型").append(typeOf(i / 256)).append(" 容量").append((i&255)+1).append("M: ")
			  .append(reserved).append('/').append(exist).append('\n');
		}

		return sb;
	}
	private static String typeOf(int i) {
		return switch (i) {
			case 0 -> "byte";
			case 1 -> "int";
			case 2 -> "char";
			default -> "unknown";
		};
	}

	public ArrayCache() {}

	@SuppressWarnings("unchecked")
	private static <T> T getGlobalArray(int idx, int size) {
		idx <<= 8;
		int i1 = size / LARGE_ARRAY_SIZE - 1;
		if (i1 >= 255) {
			idx = idx + 255;
		} else {
			idx += i1;
			if (Integer.lowestOneBit(size) != size) return null;
		}

		long offCache = Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * CACHE_COUNT * (long) idx;
		long offUsing = Unsafe.ARRAY_INT_BASE_OFFSET + ((long) idx << 2);
		for (int i = 0; i < CACHE_COUNT; i++, offCache += Unsafe.ARRAY_OBJECT_INDEX_SCALE) {
			var r = (Reference<?>) U.getReferenceVolatile(G_Cache, offCache);
			Object t;
			if (r != null && (t = r.get()) != null) {
				while (true) {
					int bits = U.getIntVolatile(G_Using, offUsing);
					if ((bits & (1<<i)) != 0) break;
					if (U.compareAndSetInt(G_Using, offUsing, bits, bits | (1<<i)))
						return (T) t;
				}
			}
		}

		return null;
	}

	private static void putGlobalArray(int idx, Object array, int size) {
		idx = idx * 256 + Math.min(size / LARGE_ARRAY_SIZE - 1, 255);

		long offCache = Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * CACHE_COUNT * (long) idx;
		long offUsing = Unsafe.ARRAY_INT_BASE_OFFSET + ((long) idx << 2);
		for (int i = 0; i < CACHE_COUNT; i++, offCache += Unsafe.ARRAY_OBJECT_INDEX_SCALE) {
			var r = (Reference<?>) U.getReferenceVolatile(G_Cache, offCache);
			if (r != null && r.get() == array) {
				int mask = 1 << i;
				while (true) {
					int bits = U.getIntVolatile(G_Using, offUsing);
					if ((bits & mask) == 0) throw new InternalError("using[i] == false");
					if (U.compareAndSetInt(G_Using, offUsing, bits, bits&~mask))
						return;
				}
			}
		}

		offCache = Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * CACHE_COUNT * (long) idx;
		for (int i = 0; i < CACHE_COUNT; i++, offCache += Unsafe.ARRAY_OBJECT_INDEX_SCALE) {
			var r = (Reference<?>) U.getReferenceVolatile(G_Cache, offCache);
			if (r == null || r.get() == null) {
				if (U.compareAndSetReference(G_Cache, offCache, r, G_Sential)) {
					var ref = i < 2 ? new SoftReference<>(array) : new WeakReference<>(array);
					U.putReferenceVolatile(G_Cache, offCache, ref);

					int mask = 1 << i;
					while (true) {
						int bits = U.getIntVolatile(G_Using, offUsing);
						if ((bits & mask) == 0 || U.compareAndSetInt(G_Using, offUsing, bits, bits&~mask))
							return;
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private <T> T getArray(int base, int size) {
		if (size < MIN_ARRAY_SIZE) return null;

		int idx = base * 13 + (23 - Integer.numberOfLeadingZeros(size));
		if (Integer.lowestOneBit(size) != size) idx++;
		base = idx * CACHE_COUNT;

		int used = using[idx];
		for (int i = 0; i < CACHE_COUNT; i++) {
			var r = (Reference<?>) cache[base+i];
			Object t;
			if (r != null && (t = r.get()) != null && (used&(1<<i)) == 0) {
				using[idx] |= 1<<i;
				return (T) t;
			}
		}

		return null;
	}
	private void putArray(int base, Object array, int size) {
		if (size < MIN_ARRAY_SIZE) return;

		int idx = base * 13 + (23 - Integer.numberOfLeadingZeros(size));
		base = idx * CACHE_COUNT;

		int used = using[idx];
		int free = -4;
		for (int i = 0; i < CACHE_COUNT; i++) {
			var r = (Reference<?>) cache[base+i];
			var t = r == null ? null : r.get();
			if (t == array) {
				using[idx] = used & ~(1<<i);
				return;
			}
			if (free < 0) {
				if (t == null) free = i;
				else if ((used&(1<<i)) != 0) free = -(i + 1);
			}
		}

		if (free < 0) free = -free - 1;
		cache[base+free] = free < 3 ? new SoftReference<>(array) : new WeakReference<>(array);
		using[idx] &= ~(1<<free);
	}

	public static byte[] getByteArray(int size, boolean fillWithZeros) {
		int size1 = (size+MIN_ARRAY_SIZE-1)& -MIN_ARRAY_SIZE;

		byte[] array = size1 > LARGE_ARRAY_SIZE ? getGlobalArray(0, size1) : small().getArray(0, size1);

		if (array == null) array = fillWithZeros ? new byte[size1] : (byte[]) U.allocateUninitializedArray(byte.class, size1);
		else if (fillWithZeros) {
			for (int i = 0; i < size; i++)
				array[i] = 0;
		}

		return array;
	}
	public static void putArray(byte[] array) {
		if (array.length > LARGE_ARRAY_SIZE) putGlobalArray(0, array, array.length);
		else small().putArray(0, array, array.length);
	}

	public static int[] getIntArray(int size, int fillWithZeros) {
		int size1 = (size+MIN_ARRAY_SIZE-1)& -MIN_ARRAY_SIZE;

		int[] array = size1 > LARGE_ARRAY_SIZE ? getGlobalArray(1, size1) : small().getArray(1, size1);

		if (array == null) array = fillWithZeros != 0 ? new int[size1] : (int[]) U.allocateUninitializedArray(int.class, size1);
		else {
			for (int i = 0; i < fillWithZeros; i++)
				array[i] = 0;
		}

		return array;
	}
	public static void putArray(int[] array) {
		if (array.length > LARGE_ARRAY_SIZE) putGlobalArray(1, array, array.length);
		else small().putArray(1, array, array.length);
	}

	public static char[] getCharArray(int size, boolean fillWithZeros) {
		// round up to CHIP_SIZE
		// 分块... 反正get实际意义是... 长度至少为N的数组
		int size1 = (size+ MIN_ARRAY_SIZE -1)& -MIN_ARRAY_SIZE;

		char[] array = size1 > LARGE_ARRAY_SIZE ? getGlobalArray(2, size1) : small().getArray(2, size1);

		if (array == null) array = fillWithZeros ? new char[size1] : (char[]) U.allocateUninitializedArray(char.class, size1);
		else if (fillWithZeros) {
			for (int i = 0; i < size; i++)
				array[i] = 0;
		}

		return array;
	}
	public static void putArray(char[] array) {
		if (array.length > LARGE_ARRAY_SIZE) putGlobalArray(2, array, array.length);
		else small().putArray(2, array, array.length);
	}
}