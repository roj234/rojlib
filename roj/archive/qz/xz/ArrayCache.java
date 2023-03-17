package roj.archive.qz.xz;

import roj.collect.LFUCache;
import roj.collect.RingBuffer;
import roj.math.MutableInt;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * CHANGE: 尽可能的归还数组
 */
public class ArrayCache {
	private static final ThreadLocal<ArrayCache> Caches = new ThreadLocal<>();

	public static ArrayCache getDefaultCache() {
		ArrayCache cache = Caches.get();
		if (cache == null) Caches.set(cache = new ArrayCache());
		return cache;
	}

	private static final int CACHEABLE_SIZE_MIN = 128;
	private static final int SIZES_MAX = 64;
	private static final int ARRAYS_MAX = 16;
	private static final int WARMUP_THRESHOLD = 4;

	private final LFUCache<MutableInt, RingBuffer<Reference<byte[]>>> byteCache = new LFUCache<>(SIZES_MAX, 1);
	private final LFUCache<MutableInt, RingBuffer<Reference<int[]>>> intCache = new LFUCache<>(SIZES_MAX, 1);
	private final ReentrantLock lock = new ReentrantLock();
	private final MutableInt val = new MutableInt();
	private int usage;

	private <T> T getArray(LFUCache<MutableInt, RingBuffer<Reference<T>>> cache, int size) {
		if (size < CACHEABLE_SIZE_MIN) return null;
		if (usage < WARMUP_THRESHOLD) return null;

		lock.lock();
		try {
			val.setValue(size/*/4096*/);
			RingBuffer<Reference<T>> stack = cache.get(val);
			if (stack == null) return null;

			T array;
			do {
				Reference<T> r = stack.removeLast();
				if (r == null) return null;

				array = r.get();
			} while (array == null);

			return array;
		} finally {
			lock.unlock();
		}
	}
	private <T> void putArray(LFUCache<MutableInt, RingBuffer<Reference<T>>> cache, T array, int size) {
		if (size < CACHEABLE_SIZE_MIN) return;

		lock.lock();
		try {
			if (++usage < WARMUP_THRESHOLD) return;

			val.setValue(size/*/4096*/);
			RingBuffer<Reference<T>> stack = cache.get(val);
			if (stack == null) {
				stack = new RingBuffer<>(16, ARRAYS_MAX);
				cache.put(new MutableInt(val), stack);
			}

			Reference<T> ref = stack.ringAddLast(new SoftReference<T>(array));
			if (ref != null) ref.clear();
		} finally {
			lock.unlock();
		}
	}

	public byte[] getByteArray(int size, boolean fillWithZeros) {
		byte[] array = getArray(byteCache, size);

		if (array == null) array = new byte[size];
		else if (fillWithZeros) {
			for (int i = 0; i < size; i++)
				array[i] = 0;
		}

		return array;
	}
	public void putArray(byte[] array) {
		putArray(byteCache, array, array.length);
	}

	public int[] getIntArray(int size, boolean fillWithZeros) {
		int[] array = getArray(intCache, size);

		if (array == null) array = new int[size];
		else if (fillWithZeros) {
			for (int i = 0; i < size; i++)
				array[i] = 0;
		}

		return array;
	}
	public void putArray(int[] array) {
		putArray(intCache, array, array.length);
	}
}
