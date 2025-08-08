package roj.concurrent;

import roj.text.logging.Logger;

import java.util.List;

/**
 * @author Roj234
 * @since 2025/09/02 23:03
 */
public interface ExecutorService extends Executor {
	Logger LOGGER = Logger.getLogger("线程池");
	Thread.UncaughtExceptionHandler LOG_HANDLER = (t, e) -> {
		if (!(e instanceof InterruptedException))
			LOGGER.error("线程"+t.getName()+"@"+t.getId()+" 遇到了未处理的异常", e);
	};

	static void cancelIfCancellable(Runnable task) {
		if (task instanceof Cancellable cancellable)
			cancellable.cancel();
	}

	void shutdown();
	List<Runnable> shutdownNow();
	boolean isShutdown();
	boolean isTerminated();
	/**
	 * 等待线程池所有的线程全部停止
	 */
	void awaitTerminationInterruptibility() throws InterruptedException;
	/**
	 * 等待当前的任务执行完成，不论是terminate还是正常运行
	 * 但是不抛出异常
	 */
	default void awaitTermination() {
		while (!isTerminated()) {
			try {
				awaitTerminationInterruptibility();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}
}
