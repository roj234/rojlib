package roj.util;

import roj.collect.WeakCache;
import roj.collect.XashMap;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Roj234
 * @since 2023/2/10 1:00
 */
public final class LeakDetector {
	private static final int mode;
	static {
		String m = System.clearProperty("roj.net.LD");
		if (m == null) m = "";

		switch (m) {
			case "none" -> mode = 0;
			default -> mode = 1;
			case "all" -> mode = 2;
		}
	}

	private static final XashMap.Builder<Object, Leak> BUILDER = WeakCache.shape(Leak.class);
	private final XashMap<Object, Leak> monitor = BUILDER.create();
	private static int counter = ThreadLocalRandom.current().nextInt();

	public static LeakDetector create() {
		if (mode == 0) return null;
		return new LeakDetector();
	}

	private LeakDetector() {}

	public void track(Object o) {
		if (!shouldTrack(o)) return;

		synchronized (monitor) {monitor.add(new Leak(o, monitor));}
	}
	public void remove(Object o) {
		synchronized (monitor) {
			Leak leak = monitor.removeKey(o);
			if (leak != null) leak.destroy();
		}
	}

	private boolean shouldTrack(Object o) {
		return mode == 2 || counter++ % 611 == 0;
	}

	static final class Leak extends WeakCache<Object> {
		private final String thread, type;
		private final Throwable trace;

		public Leak(Object ref, XashMap<Object, Leak> monitor) {
			super(ref, monitor);

			thread = Thread.currentThread().getName();
			type = ref.getClass().getName();
			trace = new Throwable("");
		}

		public void run() {
			if (owner.contains(this)) {
				System.err.println("===================== Resource LEAK =====================");
				System.err.println("Thread: " + thread);
				System.err.println("Type: " + type);
				trace.printStackTrace();
				System.err.println("===================== Resource LEAK =====================");
			}

			super.run();
		}
	}
}
