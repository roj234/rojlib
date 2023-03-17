package roj.concurrent.task;

import roj.concurrent.Scheduled;

/**
 * @author Roj233
 * @since 2022/2/21 14:42
 */
public class ScheduledRunnable extends Scheduled {
	private final Runnable task;

	public ScheduledRunnable(int interval, int delay, int count, Runnable task) {
		super(interval, delay, count);
		this.task = task;
	}

	@Override
	public Runnable getTask() {
		return task;
	}

	@Override
	public void execute() throws Exception {
		task.run();
	}
}
