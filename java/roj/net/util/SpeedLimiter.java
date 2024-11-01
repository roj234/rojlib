package roj.net.util;

/**
 * @author Roj234
 * @since 2024/11/12 0012 13:36
 */
public class SpeedLimiter {
	public SpeedLimiter(int bestMTU, int maxLatency) {
		this.bestMTU = bestMTU;
		this.maxLatency = maxLatency;
	}

	protected final int bestMTU, maxLatency;
	protected int bps;
	protected long lastTime;

	private static final long NANO_TO_SEC = 1_000_000_000;

	public void setBytePerSecond(int count) {
		bps = count;
		lastTime = System.nanoTime();
	}
	public int getBytePerSecond() {return bps;}

	public int limit(int pendingBytes) {
		if (bps == 0) return pendingBytes;

		var timePassed = System.nanoTime() - lastTime;

		int sendBytes = (int) Math.min(Integer.MAX_VALUE, bps * timePassed / NANO_TO_SEC);
		if (sendBytes < bestMTU && timePassed < maxLatency) return 0;

		sendBytes = Math.min(pendingBytes, sendBytes);

		if (timePassed > NANO_TO_SEC) {
			sendBytes = Math.min(bps * 2, sendBytes);
			lastTime = System.nanoTime();
		} else {
			int timeConsumed = (int) (sendBytes * NANO_TO_SEC / bps);
			lastTime += timeConsumed;
		}

		return sendBytes;
	}
}
