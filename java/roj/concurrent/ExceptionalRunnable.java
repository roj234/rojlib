package roj.concurrent;

/**
 * @author Roj234
 * @since 2024/11/21 16:30
 */
@Deprecated
@FunctionalInterface
public interface ExceptionalRunnable<T extends Throwable> {
	void run() throws T;
}
