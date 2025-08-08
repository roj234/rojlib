package roj.concurrent;

import java.util.concurrent.Future;

/**
 * @author Roj234
 * @since 2025/09/02 23:25
 */
public interface Cancellable {
	/**
	 * @see Future#isCancelled()
	 */
	boolean isCancelled();
	default boolean cancel() {return cancel(false);}
	/**
	 * @see Future#cancel(boolean)
	 */
	boolean cancel(boolean mayInterruptIfRunning);
}
