package roj.concurrent;

import roj.collect.SimpleList;
import roj.util.Helpers;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntConsumer;

/**
 * @author Roj234
 * @since 2023/2/2 0002 14:50
 */
public class GCHook extends FastLocalThread {
	public static final int SMALL_GC = 1, FULL_GC = 2;

//	private static ReferenceQueue<Object> ON_REF_THREAD;
//	private static void createOnRefThread() {
//		if (ON_REF_THREAD != null) return;
//		try {
//			String trick = "yv66vgAAADMAGgEAEGphdmEvbGFuZy9yZWYvUkwHAAEBABxqYXZhL2xhbmcvcmVmL1JlZmVy" +
//				"ZW5jZVF1ZXVlBwADAQAGPGluaXQ+AQADKClWAQAEQ29kZQwABQAGCgAEAAgBAAdlbnF1ZXVlAQAcKExqYXZ" +
//				"hL2xhbmcvcmVmL1JlZmVyZW5jZTspWgEAF2phdmEvbGFuZy9yZWYvUmVmZXJlbmNlBwAMAQAFcXVldWUBAB" +
//				"5MamF2YS9sYW5nL3JlZi9SZWZlcmVuY2VRdWV1ZTsMAA4ADwkADQAQAQASamF2YS9sYW5nL1J1bm5hYmxlB" +
//				"wASAQADcnVuDAAUAAYLABMAFQEACEVOUVVFVUVEDAAXAA8JAAIAGAAhAAIABAAAAAAAAgABAAUABgABAAcA" +
//				"AAARAAEAAQAAAAUqtwAJsQAAAAAAAAAKAAsAAQAHAAAAKAACAAIAAAAcK7QAESqmABUrwAATuQAWAQArsgA" +
//				"ZtQARBKwDrAAAAAAAAA==";
//			ByteList buf = IOUtil.getSharedByteBuf();
//			Base64.decode(trick, buf);
//			Class<?> klass = FieldAccessor.u.defineClass("java/lang/ref/RL", buf.list, 0, buf.wIndex(), null, null);
//			Constructor<?> c = klass.getDeclaredConstructor();
//			c.setAccessible(true);
//			ON_REF_THREAD = Helpers.cast(c.newInstance());
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//	}

	static {
		GCHook cl = new GCHook();
		cl.setDaemon(true);
		cl.setName("GC Status Hook");
		cl.start();
	}

	public static void register(IntConsumer cb, int type) {
		lock.lock();
		if ((type & SMALL_GC) != 0) weakCallbacks.add(cb);
		if ((type & FULL_GC) != 0) softCallbacks.add(cb);
		lock.unlock();
	}

	public static void unregister(IntConsumer cb) {
		lock.lock();
		weakCallbacks.remove(cb);
		softCallbacks.remove(cb);
		lock.unlock();
	}

	private static final List<IntConsumer> weakCallbacks = new SimpleList<>(), softCallbacks = new SimpleList<>();
	private static final ReentrantLock lock = new ReentrantLock(true);

	@Override
	public void run() {
		ReferenceQueue<Object> queue = new ReferenceQueue<>();
		Reference<Object> s, w;

		s = new SoftReference<>(o(), queue);
		w = new WeakReference<>(o(), queue);

		while (true) {
			try {
				queue.remove();
			} catch (InterruptedException e) {
				Helpers.athrow(e);
				continue;
			}

			lock.lock();
			boolean notified = false;
			if (s.get() == null) {
				notified = true;
				for (int i = softCallbacks.size() - 1; i >= 0; i--) {
					IntConsumer c = softCallbacks.get(i);
					try {
						c.accept(FULL_GC);
					} catch (Throwable e) {
						e.printStackTrace();
					}
				}

				s = new SoftReference<>(o(), queue);
			}
			if (w.get() == null) {
				if (!notified) {
					for (int i = weakCallbacks.size() - 1; i >= 0; i--) {
						IntConsumer c = weakCallbacks.get(i);
						try {
							c.accept(SMALL_GC);
						} catch (Throwable e) {
							e.printStackTrace();
						}
					}
				}

				w = new WeakReference<>(o(), queue);
			}
			lock.unlock();

			LockSupport.parkNanos(1000000L);
		}
	}

	private static Object o() { return new Object(); }
}
