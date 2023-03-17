package roj.manage;

import roj.text.TextUtil;

/**
 * @author Roj234
 * @since 2020/11/28 23:07
 */
public class PerformanceLogger implements Runnable {
	public PerformanceLogger() {

	}

	/**
	 * When an object implementing interface <code>Runnable</code> is used
	 * to create a thread, starting the thread causes the object's
	 * <code>run</code> method to be called in that separately executing
	 * thread.
	 * <p>
	 * The general contract of the method <code>run</code> is that it may
	 * take any action whatsoever.
	 *
	 * @see Thread#run()
	 */
	@Override
	public void run() {
		Thread current = Thread.currentThread();

		while (!current.isInterrupted()) {
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				break;
			}
			System.out.println("Mem: " + TextUtil.scaledNumber(SystemInfo.getMemoryUsed()));
		}
	}
}
