package roj.util;

/**
 * @author Roj233
 * @since 2022/1/23 21:51
 */
public final class SleepingBeauty extends Thread {
	static volatile SleepingBeauty run;

	private SleepingBeauty() {
		setDaemon(true);
		setName("睡美人");
	}

	public static synchronized void sleep() {
		if (run != null) return;
		Thread t = run = new SleepingBeauty();
		t.start();
	}

	public static synchronized void wakeup() {
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
