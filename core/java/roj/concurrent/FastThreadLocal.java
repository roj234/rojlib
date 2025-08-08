package roj.concurrent;

import roj.collect.BitSet;
import roj.collect.IntMap;
import roj.reflect.Unaligned;
import roj.util.ArrayCache;

import java.util.function.Supplier;

/**
 * @author Roj233
 * @since 2021/9/13 12:48
 */
public class FastThreadLocal<T> {
	private static int registrations;
	private static final BitSet reusable = new BitSet();
	private static final Object LOCK = new Object();
	private static final Object NULL = IntMap.UNDEFINED;

	private static Thread[] threads;

	private int seqNum;
	private final ThreadLocal<T> fallback = ThreadLocal.withInitial(FastThreadLocal.this::initialValue);

	public FastThreadLocal() {
		synchronized (LOCK) {
			int firstUsable = reusable.first();
			if (firstUsable >= 0) {
				reusable.remove(firstUsable);
				this.seqNum = firstUsable;
			} else {
				this.seqNum = registrations++;
			}
		}
	}

	public static <T> FastThreadLocal<T> withInitial(Supplier<T> s) {
		return new FastThreadLocal<T>() {
			protected T initialValue() { return s.get(); }
		};
	}

	public static void clear() {
		var t = Thread.currentThread();
		try {
			Unaligned.U.putReference(t, Unaligned.fieldOffset(Thread.class, "threadLocals"), null);
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (t instanceof FastLocalThread flt) {
			flt.localDataArray = ArrayCache.OBJECTS;
		}
	}

	public void destroy() {
		removeAll();
		synchronized (LOCK) {
			reusable.add(seqNum);
		}
		seqNum = -1;
	}

	public void removeAll() {
		ThreadGroup g = Thread.currentThread().getThreadGroup();
		while (g.getParent() != null) g = g.getParent();

		synchronized (LOCK) {
			Thread[] t = threads;
			int c = g.activeCount();
			if (t == null || t.length < c) {
				// 不同步就浪费一个array而已
				t = threads = new Thread[c];
			}

			c = g.enumerate(t);
			while (c-- > 0) {
				if (t[c] instanceof FastLocalThread flt) {
					if (flt.localDataArray.length >= seqNum) {
						synchronized (flt.arrayLock) {
							// 要么没复制，要么复制完了而且新数组已设置
							flt.localDataArray[seqNum] = null;
						}
					}
				} else {
					Object map = LazyThreadLocal.ACCESS.getMap(fallback, t[c]);
					if (map != null) LazyThreadLocal.ACCESS.remove(map, fallback);
				}
			}
		}
	}

	protected T initialValue() { return null; }

	@SuppressWarnings("unchecked")
	public T get() {
		Thread t = Thread.currentThread();
		if (!(t instanceof FastLocalThread)) return fallback.get();

		Object[] dataHolder = getDataHolder((FastLocalThread) t, seqNum);
		Object v = dataHolder[seqNum];
		if (v == null) {
			v = initialValue();
			dataHolder[seqNum] = v == null ? NULL : v;
		}
		return v == NULL ? null : (T) v;
	}
	public void set(T v) {
		Thread t = Thread.currentThread();
		if (!(t instanceof FastLocalThread)) fallback.set(v);
		else getDataHolder((FastLocalThread) t, seqNum)[seqNum] = v == null ? NULL : v;
	}
	public void remove() {
		Thread t = Thread.currentThread();
		if (!(t instanceof FastLocalThread)) fallback.remove();
		else {
			Object[] dataHolder = getDataHolder((FastLocalThread) t, -1);
			if (dataHolder.length > seqNum) dataHolder[seqNum] = null;
		}
	}

	private static Object[] getDataHolder(FastLocalThread t1, int seqNum) {
		Object[] data = t1.localDataArray;
		if (data.length <= seqNum) {
			Object[] oldArray = data;
			data = new Object[seqNum+1];
			synchronized (t1.arrayLock) {
				if (oldArray.length > 0) {
					System.arraycopy(oldArray, 0, data, 0, oldArray.length);
				}
				t1.localDataArray = data;
			}
		}
		return data;
	}
}