package roj.concurrent;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public interface Task {
	default boolean isCancelled() { return false; }
	default boolean cancel() { return false; }

	void execute() throws Exception;
}