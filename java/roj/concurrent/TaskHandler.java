package roj.concurrent;

import roj.concurrent.task.AsyncTask;
import roj.concurrent.task.ITask;
import roj.util.Helpers;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * @author Roj234
 * @since 2020/11/30 23:07
 */
public interface TaskHandler {
	default void pushTask(Callable<ITask> lazyTask) throws Exception {
		AsyncTask<ITask> waiter = new AsyncTask<>(lazyTask);
		pushTask(waiter);
		try {
			pushTask(waiter.get());
		} catch (ExecutionException e) {
			Helpers.athrow(e.getCause());
		}
	}

	void pushTask(ITask task);
	void clearTasks();
}