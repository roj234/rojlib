package roj.concurrent.timing;

import roj.concurrent.task.ITask;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public abstract class Scheduled implements ITask {
	Scheduler owner;
	volatile Scheduled next;

	long nextRun;

	private final int interval;
	private int count;
	private boolean sync;

	public Scheduled(int interval, int delay, int count) {
		if (interval <= 0 && count != 1) throw new IllegalArgumentException("interval <= 0");
		if (delay < 0) throw new IllegalArgumentException("delay < 0");

		this.interval = interval;
		this.nextRun = System.currentTimeMillis() + delay;
		this.count = count;
	}

	public Scheduled sync() {
		sync = true;
		return this;
	}
	protected boolean forceOnScheduler() { return sync; }

	public final int getRemainCount() { return count; }
	public final long getNextRun() { return nextRun; }

	public synchronized final void cancel() { nextRun = -2; }
	public boolean cancel(boolean force) { nextRun = -2; return true; }
	public final boolean isCancelled() { return nextRun < 0; }

	public void execute() throws Exception {
		try {
			execute1();
		} catch (Throwable e) {
			cancel();
			throw e;
		}
	}
	protected abstract void execute1() throws Exception;

	protected synchronized boolean schedule(long time) {
		if (nextRun < 0) return false;

		if (--count == 0) {
			nextRun = -1;
			return false;
		} else {
			nextRun = time + interval;
			return true;
		}
	}
}
