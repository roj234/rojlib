package roj.concurrent.task;

import roj.concurrent.Scheduled;

/**
 * @author Roj233
 * @since 2022/2/21 14:42
 */
public class ScheduledTask extends Scheduled {
	private final ITask task;

	public ScheduledTask(int interval, int delay, int remain, ITask task) {
		super(interval, delay, remain);
		this.task = task;
	}

	public ScheduledTask(int delay, ITask task) {
		super(delay);
		this.task = task;
	}

	@Override
	public ITask getTask() {
		return task;
	}

	@Override
	public void execute() throws Exception {
		task.execute();
	}
}
