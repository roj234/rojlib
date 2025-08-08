package roj.concurrent;

import roj.util.function.ExceptionalRunnable;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public interface Task extends ExceptionalRunnable<Exception> {
	void run() throws Exception;
}