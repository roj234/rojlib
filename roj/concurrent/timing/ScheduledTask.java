package roj.concurrent.timing;

import roj.concurrent.task.ITask;

/**
 * @author Roj233
 * @since 2022/2/21 14:42
 */
public class ScheduledTask extends Scheduled {
	private final ITask task;

	public ScheduledTask(ITask task, int delay, int interval, int count) {
		super(interval, delay, count);
		this.task = task;
	}

	@Override
	protected void execute1() throws Exception {
		task.execute();
	}
}
