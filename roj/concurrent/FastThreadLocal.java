package roj.concurrent;

import roj.collect.IntMap;
import roj.collect.MyBitSet;
import roj.reflect.DirectAccessor;
import roj.util.Helpers;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * @author Roj233
 * @since 2021/9/13 12:48
 */
public class FastThreadLocal<T> {
	private final ThreadLocal<T> fallback = new ThreadLocal<>();

	private static int registrations;
	private static final MyBitSet reusable = new MyBitSet();
	private static final Object NULL = IntMap.UNDEFINED;

	private static Thread[] threads;
	private static BiFunction<Object, Object, Object> getMap;
	private static BiConsumer<Object, Object> remove;

	private int seqNum;

	public FastThreadLocal() {
		synchronized (reusable) {
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

	public void destroy() {
		removeAll();
		synchronized (reusable) {
			reusable.add(seqNum);
		}
		seqNum = -1;
	}

	@SuppressWarnings("unchecked")
	public void removeAll() {
		ThreadGroup g = Thread.currentThread().getThreadGroup();
		while (g.getParent() != null) g = g.getParent();

		synchronized (reusable) {
			Thread[] t = threads;
			int c = g.activeCount();
			if (t == null || t.length < c) {
				// 不同步就浪费一个array而已
				t = threads = new Thread[c];
			}

			c = g.enumerate(t);
			while (c-- > 0) {
				if (t[c] instanceof FastLocalThread) {
					FastLocalThread flt = (FastLocalThread) t[c];
					if (flt.localDataArray.length >= seqNum) {
						synchronized (flt.arrayLock) {
							// 要么没复制，要么复制完了而且新数组已设置
							flt.localDataArray[seqNum] = null;
						}
					}
				} else {
					if (getMap == null) {
						try {
							getMap = DirectAccessor.builder(BiFunction.class).delegate_o(ThreadLocal.class, "getMap", "apply").build();
							remove = DirectAccessor.builder(BiConsumer.class).delegate_o(getMap.apply(fallback, Thread.currentThread()).getClass(), "remove", "accept").build();
						} catch (Throwable e) {
							e.printStackTrace();
						}
					}
					if (getMap == null) continue;
					remove.accept(getMap.apply(fallback, t[c]), fallback);
				}
			}
		}
	}

	protected T initialValue() { return null; }

	@SuppressWarnings("unchecked")
	public T get() {
		Thread t = Thread.currentThread();
		if (!(t instanceof FastLocalThread)) {
			T t1 = fallback.get();
			if (t1 == null) {
				t1 = initialValue();
				fallback.set(t1 == null ? Helpers.cast(NULL) : t1);
			}
			return t1 == NULL ? null : t1;
		}
		Object[] dataHolder = getDataHolder((FastLocalThread) t, seqNum);

		Object v = dataHolder[seqNum];
		if (v == null) {
			v = initialValue();
			dataHolder[seqNum] = v == null ? NULL : v;
		} else if (v == NULL) return null;
		return (T) v;
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
