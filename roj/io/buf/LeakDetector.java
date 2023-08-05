package roj.io.buf;

import roj.collect.WeakHashSet;

import java.lang.ref.ReferenceQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Roj234
 * @since 2023/2/10 0010 1:00
 */
public final class LeakDetector extends WeakHashSet<Object> {
	private static final int mode;

	private static int counter = (int) System.currentTimeMillis();

	private static final WeakHashSet<LeakDetector> ref = newWeakHashSet();
	private static final ReentrantLock checkLock = new ReentrantLock();

	static {
		String m = System.clearProperty("roj.net.LD");
		if (m == null) m = "";

		switch (m) {
			case "none": mode = 0; break;
			default:
			case "some": mode = 1; break;
			case "all":
				mode = 2;
				Thread t = new Thread(() -> {
					while (true) {
						checkLeak();
						LockSupport.parkNanos(100_000_000L);
					}
				});
				t.setName("LeakDetector");
				t.setDaemon(true);
				t.start();
				break;
		}
	}

	public static LeakDetector create() {
		if (mode == 0) return null;
		return new LeakDetector();
	}

	public static void checkLeak() {
		if (!checkLock.tryLock()) return;
		try {
			for (LeakDetector ld : ref) ld.doEvict();
		} finally {
			checkLock.unlock();
		}
	}

	private LeakDetector() {
		checkLock.lock();
		try {
			ref.add(this);
		} finally {
			checkLock.unlock();
		}
	}

	public void track(Object o) {
		if (!shouldTrack(o)) return;
		findOrAdd(o, true);
	}

	private boolean shouldTrack(Object o) {
		return mode == 2 || counter++ % 611 == 0;
	}

	protected Entry createEntry(Object key, ReferenceQueue<Object> queue) { return new LD(key, queue); }
	protected void entryRemoved(Entry entry, boolean byGC) {
		LD ld = (LD) entry;

		if (byGC) ld.report();
		else ld.clear();
	}

	static final class LD extends WeakHashSet.Entry {
		private final String thread, type;
		private final Throwable trace;

		public LD(Object ref, ReferenceQueue<Object> queue) {
			super(ref, queue);

			thread = Thread.currentThread().getName();
			type = ref.getClass().getName();
			trace = new Throwable("");
		}

		public void report() {
			System.err.println("===================== Resource LEAK =====================");
			System.err.println("Thread: " + thread);
			System.err.println("Type: " + type);
			trace.printStackTrace();
			System.err.println("===================== Resource LEAK =====================");
		}
	}
}
