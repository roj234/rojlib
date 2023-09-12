package roj.concurrent.timing;

import roj.concurrent.task.ITask;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class ScheduledTask implements ITask {
	private final ITask task;

	Scheduler owner;
	volatile ScheduledTask next;

	long nextRun;

	private final int interval;
	private int count;
	private boolean sync;

	public ScheduledTask(ITask task, int interval, int delay, int count) {
		if (interval <= 0 && count != 1) throw new IllegalArgumentException("interval <= 0");
		if (delay < 0) throw new IllegalArgumentException("delay < 0");

		this.task = task;
		this.interval = interval;
		this.nextRun = System.currentTimeMillis() + delay;
		this.count = count;
	}

	public ScheduledTask sync() {
		sync = true;
		return this;
	}
	protected boolean forceOnScheduler() { return sync; }

	public final int getRemainCount() { return count; }
	public final long getNextRun() { return nextRun; }

	public synchronized final void cancel() { nextRun = -2; }
	public synchronized boolean cancel(boolean force) { nextRun = -2; return true; }
	public final boolean isCancelled() { return nextRun == -2; }

	public void execute() throws Exception {
		try {
			task.execute();
		} catch (Throwable e) {
			cancel();
			throw e;
		}
	}

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
