package roj.net.mychat;

/**
 * @author Roj233
 * @since 2022/3/16 22:45
 */
public class TimeCounter {
	private final int reset;
	private long begin, last;
	private int count;

	public TimeCounter(int reset) {
		this.reset = reset;
	}

	// per second
	public float plus() {
		long t = System.currentTimeMillis();
		if (t - last > reset) {
			count = 1;
			begin = last = t;
			return 0;
		} else {
			last = t;
			return 1000f * ++count / (t - begin);
		}
	}
}
