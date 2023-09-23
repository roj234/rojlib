package roj.util;

import roj.collect.LFUCache;
import roj.math.MutableInt;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class ArrayCache {
	public static final byte[] BYTES = new byte[0];
	public static final char[] CHARS = new char[0];
	public static final int[] INTS = new int[0];
	public static final float[] FLOATS = new float[0];
	public static final double[] DOUBLES = new double[0];
	public static final long[] LONGS = new long[0];
	public static final Object[] OBJECTS = new Object[0];
	public static final Class<?>[] CLASSES = new Class<?>[0];

	private static final ThreadLocal<ArrayCache> CACHE = new ThreadLocal<>();
	public static ArrayCache getDefaultCache() {
		ArrayCache cache = CACHE.get();
		if (cache == null) CACHE.set(cache = new ArrayCache());
		return cache;
	}

	private static final int LARGE_ARRAY_SIZE = 524288;
	private static final int CHIP_SIZE = 256;
	private static final int SIZES_MAX = 64;
	private static final int ARRAYS_MAX = 9;

	private final LFUCache<MutableInt, Reference<byte[]>[]> byteCache = new LFUCache<>(SIZES_MAX, 1);
	private final LFUCache<MutableInt, Reference<char[]>[]> charCache = new LFUCache<>(SIZES_MAX, 1);
	private final LFUCache<MutableInt, Reference<int[]>[]> intCache = new LFUCache<>(SIZES_MAX, 1);
	private final ReentrantLock lock = new ReentrantLock();
	private final MutableInt val = new MutableInt();

	public ArrayCache() {}

	private <T> T getArray(Map<MutableInt, Reference<T>[]> cache, int size) {
		if (size < CHIP_SIZE) return null;

		lock.lock();
		try {
			val.setValue(size/CHIP_SIZE);
			Reference<T>[] stack = cache.get(val);
			if (stack == null) return null;

			for (int i = 0; i < stack.length; i++) {
				Reference<T> r = stack[i];
				if (r == null) continue;

				stack[i] = null;
				T t = r.get();
				if (t != null) return t;
			}
			return null;
		} finally {
			lock.unlock();
		}
	}
	private <T> void putArray(Map<MutableInt, Reference<T>[]> cache, T array, int size) {
		if (size < CHIP_SIZE) return;

		lock.lock();
		try {
			val.setValue(size/CHIP_SIZE);
			Reference<T>[] stack = cache.get(val);
			if (stack == null) {
				cache.put(new MutableInt(val), stack = Helpers.cast(new Reference<?>[8]));
			}

			for (int i = 0; i < stack.length; i++) {
				Reference<T> r = stack[i];
				if (r == null || r.get() == null) {
					stack[i] = i == 0 ? new SoftReference<>(array) : new WeakReference<>(array);
					break;
				}
			}
		} finally {
			lock.unlock();
		}
	}

	public byte[] getByteArray(int size, boolean fillWithZeros) {
		int size1 = (size+CHIP_SIZE-1)& -CHIP_SIZE;

		byte[] array = getArray(byteCache, size1);

		if (array == null) array = new byte[size1];
		else if (fillWithZeros) {
			for (int i = 0; i < size; i++)
				array[i] = 0;
		}

		return array;
	}
	public void putArray(byte[] array) {
		putArray(byteCache, array, array.length);
	}

	public int[] getIntArray(int size, int fillWithZeros) {
		int size1 = (size+CHIP_SIZE-1)& -CHIP_SIZE;

		int[] array = getArray(intCache, size1);

		if (array == null) array = new int[size1];
		else {
			for (int i = 0; i < fillWithZeros; i++)
				array[i] = 0;
		}

		return array;
	}
	public void putArray(int[] array) {
		putArray(intCache, array, array.length);
	}

	public char[] getCharArray(int size, boolean fillWithZeros) {
		// round up to CHIP_SIZE
		// 分块... 反正get实际意义是... 长度至少为N的数组
		int size1 = (size+CHIP_SIZE-1)& -CHIP_SIZE;

		char[] array = getArray(charCache, size1);

		if (array == null) array = new char[size1];
		else if (fillWithZeros) {
			for (int i = 0; i < size; i++)
				array[i] = 0;
		}

		return array;
	}
	public void putArray(char[] array) {
		putArray(charCache, array, array.length);
	}
}
