package roj.io.buf;

import roj.collect.IntMap;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Roj234
 * @since 2023/2/10 0010 1:00
 */
public final class LeakDetector {
	private static final int mode;

	private static int counter = (int) System.currentTimeMillis();

	private static final ReferenceQueue<Object> leaked = new ReferenceQueue<>();
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
		while (true) {
			LD ld = (LD) leaked.poll();
			if (ld == null) break;
			ld.check();
		}

		checkLock.unlock();
	}

	private final IntMap<LD> tracked = new IntMap<>();

	private LeakDetector() {}

	public void track(Object o) {
		if (!shouldTrack(o)) return;
		checkLeak();

		LD prev = tracked.putInt(System.identityHashCode(o), new LD(o));
		if (prev != null) prev.released = true;
	}

	public void untrack(Object o) {
		LD ld = tracked.remove(System.identityHashCode(o));
		if (ld != null) ld.released = true;

		checkLeak();
	}

	private boolean shouldTrack(Object o) {
		return mode == 2 || counter++ % 611 == 0;
	}

	static final class LD extends PhantomReference<Object> {
		private final String thread, type;
		private final Throwable trace;
		boolean released;

		public LD(Object ref) {
			super(ref, leaked);
			thread = Thread.currentThread().getName();
			type = ref.getClass().getSimpleName();
			trace = new Throwable("");
		}

		public void check() {
			if (released) return;

			System.err.println("===================== Resource LEAK =====================");
			System.out.println("Thread: " + thread);
			System.out.println("Type: " + type);
			trace.printStackTrace();
			System.err.println("===================== Resource LEAK =====================");
		}
	}
}
