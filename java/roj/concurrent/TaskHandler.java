package roj.concurrent;

import roj.concurrent.task.AsyncTask;
import roj.concurrent.task.ITask;
import roj.util.Helpers;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * @author Roj234
 * @since 2020/11/30 23:07
 */
public interface TaskHandler {
	default void pushTask(Callable<ITask> lazyTask) throws Exception {
		var waiter = new AsyncTask<>(lazyTask);
		submit(waiter);
		try {
			submit(waiter.get());
		} catch (ExecutionException e) {
			Helpers.athrow(e.getCause());
		}
	}

	void submit(ITask task);
	void shutdown();
	List<ITask> shutdownNow();
	default void shutdownAndCancel() { for (ITask task : shutdownNow()) task.cancel(); }
	boolean isShutdown();
	boolean isTerminated();
	/**
	 * 等待当前的任务执行完成，不论是terminate还是正常运行
	 */
	@Deprecated
	void awaitTermination() throws InterruptedException;
	/**
	 * 等待当前的任务执行完成，不论是terminate还是正常运行
	 * 但是不抛出异常
	 */
	@Deprecated
	default boolean awaitFinish() {
		try {
			awaitTermination();
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}