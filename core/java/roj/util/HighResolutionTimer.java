package roj.util;

import roj.concurrent.FastThreadLocal;

/**
 * @author Roj233
 * @since 2022/1/23 21:51
 */
public final class HighResolutionTimer extends Thread {
	private HighResolutionTimer() {setName("睡美人");setDaemon(true);}

	private static boolean running;
	public static void activate() {
		if (!running) {
			running = true;
			new HighResolutionTimer().start();
		}
	}
	public static void runThis() {
		FastThreadLocal.clear();

		running = true;
		if (!Thread.currentThread().isDaemon())
			Thread.currentThread().setName("睡美人(Non Daemon)");
		while (true) {
			try {
				Thread.sleep(Long.MAX_VALUE);
			} catch (InterruptedException e) {
				break;
			}
		}
		System.out.println("草，我是睡美人啊");
	}

	@Override public void run() {runThis();}
}