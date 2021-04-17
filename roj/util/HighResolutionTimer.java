package roj.util;

/**
 * @author Roj233
 * @since 2022/1/23 21:51
 */
public final class HighResolutionTimer extends Thread {
	static volatile HighResolutionTimer run;

	private HighResolutionTimer() {
		setDaemon(true);
		setName("睡美人");
	}

	public static synchronized void activate() {
		if (run != null) return;
		Thread t = run = new HighResolutionTimer();
		t.start();
	}

	public static synchronized void deactivate() {
		if (run == null) return;
		Thread t = run;
		run = null;
		t.interrupt();
	}

	@Override
	public void run() {
		while (run != null) {
			try {
				Thread.sleep(Long.MAX_VALUE);
			} catch (InterruptedException ignored) {}
		}
		System.out.println("草，我是睡美人啊");
	}
}
