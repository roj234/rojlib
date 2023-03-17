package roj.concurrent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roj234
 * @since 2021/2/17 23:08
 */
public class PrefixFactory implements TaskPool.MyThreadFactory {
	final String prefix;
	final AtomicInteger ordinal = new AtomicInteger(1);

	public PrefixFactory(String prefix) {
		this.prefix = prefix;
	}

	@Override
	public TaskPool.ExecutorImpl get(TaskPool pool) {
		return pool.new ExecutorImpl(prefix + '-' + ordinal.getAndIncrement());
	}
}
