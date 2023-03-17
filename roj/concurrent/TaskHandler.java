package roj.concurrent;

import roj.concurrent.task.ITask;

/**
 * @author Roj234
 * @since 2020/11/30 23:07
 */
public interface TaskHandler {
	void pushTask(ITask task);
	void clearTasks();
}
