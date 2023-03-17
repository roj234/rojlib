package roj.concurrent;

import roj.collect.IntMap;
import roj.collect.MyBitSet;
import roj.reflect.DirectAccessor;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * @author Roj233
 * @since 2021/9/13 12:48
 */
public class FastThreadLocal<T> {
	private static final ThreadLocal<Object[]> slowGetter = new ThreadLocal<>();

	private static int registrations;
	private static final MyBitSet reusable = new MyBitSet();
	private static final Object NULL = IntMap.UNDEFINED;

	private static Thread[] threads;
	private static BiFunction<Object, Object, Object> getMap;
	private static BiConsumer<Object, Object> remove;

	@SuppressWarnings("unchecked")
	private static void init() {
		try {
			getMap = DirectAccessor.builder(BiFunction.class).delegate_o(ThreadLocal.class, "getMap", "apply").build();
			remove = DirectAccessor.builder(BiConsumer.class).delegate_o(getMap.apply(slowGetter, Thread.currentThread()).getClass(), "remove", "accept").build();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	private int seqNum;

	public int seqNum() {
		return seqNum;
	}

	public FastThreadLocal() {
		synchronized (slowGetter) {
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
			@Override
			protected T initialValue() {
				return s.get();
			}
		};
	}

	public void kill() {
		removeAll();
		synchronized (slowGetter) {
			reusable.add(seqNum);
		}
		seqNum = -1;
	}

	public void removeAll() {
		ThreadGroup g = Thread.currentThread().getThreadGroup();
		while (g.getParent() != null) g = g.getParent();

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
				if (getMap == null) init();
				if (getMap == null) continue;
				remove.accept(getMap.apply(slowGetter, t[c]), slowGetter);
			}
		}
	}

	protected T initialValue() {
		return null;
	}

	@SuppressWarnings("unchecked")
	public T get() {
		Object[] x = getDataHolder(seqNum);
		Object v = x[seqNum];
		if (v == null) {
			v = initialValue();
			x[seqNum] = v == null ? NULL : v;
		} else if (v == NULL) return null;
		return (T) v;
	}

	public void set(T v) {
		// 这样就不用初始化数组, 而且调用getDataHolder的时候也方便判断...大概吧
		getDataHolder(seqNum)[seqNum] = v == null ? NULL : v;
	}

	public static Object NullId() {
		return NULL;
	}

	public static Object[] getDataHolder(int seqNum) {
		Thread t = Thread.currentThread();
		if (t instanceof FastLocalThread) {
			FastLocalThread t1 = (FastLocalThread) t;
			Object[] data = t1.localDataArray;
			if (data.length <= seqNum) {
				Object[] oldArray = data;
				data = new Object[seqNum + 1];
				synchronized (t1.arrayLock) {
					if (oldArray.length > 0) {
						System.arraycopy(oldArray, 0, data, 0, oldArray.length);
					}
					t1.localDataArray = data;
				}
			}
			return data;
		}

		Object[] x = slowGetter.get();
		if (x == null || x.length <= seqNum) {
			Object[] oldArray = x;
			x = new Object[seqNum + 1];
			if (oldArray != null) {
				System.arraycopy(oldArray, 0, x, 0, oldArray.length);
			}
			slowGetter.set(x);
		}
		return x;
	}

	@Deprecated
	public void remove() {
		getDataHolder(seqNum)[seqNum] = null;
	}
}
