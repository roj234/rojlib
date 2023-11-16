package roj.concurrent.timing;

import roj.concurrent.task.ITask;
import roj.reflect.ReflectionUtils;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class ScheduledTask implements ITask {
	private final ITask task;

	Scheduler owner;
	volatile ScheduledTask next;

	private static final long u_nextRun = ReflectionUtils.fieldOffset(ScheduledTask.class, "nextRun");
	volatile long nextRun;

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

	public final int getExecutionCountLeft() { return count; }
	public final long getNextRunTime() { return nextRun; }

	public final void cancel() { cancel(false); }
	public final boolean cancel(boolean force) {
		boolean cancelled = isCancelled();
		nextRun = -2;
		return cancelled;
	}
	public final boolean isCancelled() { return nextRun == -2; }

	public void execute() throws Exception {
		try {
			task.execute();
		} catch (Throwable e) {
			cancel();
			throw e;
		}
	}

	protected boolean scheduleNext(long currentTime) {
		while (true) {
			long t = nextRun;
			if (t < 0) return false;

			if (count == 1) { // FINAL
				if (u.compareAndSwapLong(this, u_nextRun, t, -1)) {
					count--;
					return false;
				}
			} else {
				if (u.compareAndSwapLong(this, u_nextRun, t, currentTime + interval)) {
					count--;
					return true;
				}
			}
		}
	}
}
