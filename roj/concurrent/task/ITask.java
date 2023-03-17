package roj.concurrent.task;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public interface ITask {
	default boolean isCancelled() {
		return false;
	}
	default boolean cancel(boolean force) {
		return false;
	}

	void execute() throws Exception;

	default boolean continueExecuting() {
		return false;
	}
}
