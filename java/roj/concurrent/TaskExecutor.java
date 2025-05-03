package roj.concurrent;

import roj.util.Helpers;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @author Roj234
 * @since 2020/11/30 23:07
 */
public interface TaskExecutor {
	default void pushTask(Callable<Task> lazyTask) throws Exception {
		var waiter = new AsyncTask<>(lazyTask);
		submit(waiter);
		try {
			submit(waiter.get());
		} catch (ExecutionException e) {
			Helpers.athrow(e.getCause());
		}
	}

	default <T> Future<T> submit(Callable<T> task) {
		var ftask = new AsyncTask<>(Objects.requireNonNull(task));
		submit(ftask);
		return ftask;
	}
	void submit(Task task);
	void shutdown();
	List<Task> shutdownNow();
	default void shutdownAndCancel() { for (Task task : shutdownNow()) task.cancel(); }
	boolean isShutdown();
	boolean isTerminated();
	/**
	 * 等待当前的任务执行完成，不论是terminate还是正常运行
	 * @deprecated 由于不关闭线程池的add方法，到底等待了谁执行完成是未定义的
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