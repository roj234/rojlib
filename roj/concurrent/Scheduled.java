package roj.concurrent;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public abstract class Scheduled {
	public final int interval;
	TaskSequencer owner;
	long nextRun;
	int count;
	volatile boolean cancelled;

	public Scheduled(int interval, int delay, int count) {
		if (interval <= 0 && count != 1) throw new IllegalArgumentException("interval <= 0");
		if (delay < 0) throw new IllegalArgumentException("delay < 0");

		this.interval = interval;
		this.nextRun = System.currentTimeMillis() + delay;
		this.count = count;
	}

	public Scheduled(int delay) {
		if (delay < 0) throw new IllegalArgumentException("delay < 0");

		this.interval = -1;
		this.nextRun = System.currentTimeMillis() + delay;
		this.count = 1;
	}

	public abstract Object getTask();

	public int getRemainCount() {
		return count;
	}

	public final long getNextRun() {
		return nextRun;
	}

	public void cancel() {
		cancelled = true;
		nextRun = -2;
	}

	public boolean isCancelled() {
		return cancelled;
	}

	public abstract void execute() throws Exception;
}
