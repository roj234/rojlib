package roj.util;

/**
 * @author Roj233
 * @since 2022/1/23 21:51
 */
public final class HighResolutionTimer extends Thread {
	private HighResolutionTimer() {setName("睡美人");}

	static {new HighResolutionTimer().start();}
	public static void activate() {}

	@Override
	public void run() {
		while (true) {
			try {
				Thread.sleep(Long.MAX_VALUE);
			} catch (InterruptedException e) {
				break;
			}
		}
		System.out.println("草，我是睡美人啊");
	}
}