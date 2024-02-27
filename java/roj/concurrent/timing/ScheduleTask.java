package roj.concurrent.timing;

import roj.concurrent.task.ITask;

/**
 * @author Roj234
 * @since 2024/3/6 2:27
 */
public interface ScheduleTask {
	ITask getTask();

	boolean isExpired();
	boolean isCancelled();
	boolean cancel();
}