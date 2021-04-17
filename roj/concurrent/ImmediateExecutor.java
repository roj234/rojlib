package roj.concurrent;

import roj.concurrent.task.ITask;
import roj.util.Helpers;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author solo6975
 * @since 2022/1/24 21:04
 */
public final class ImmediateExecutor extends FastLocalThread {
	private static final AtomicInteger i = new AtomicInteger();

	private final ITask task;

	public ImmediateExecutor(ITask task) {
		this.task = task;
		setDaemon(true);
		setName("ImmediateExecutor #" + i.getAndIncrement());
	}

	@Override
	public void run() {
		if (task instanceof Runnable) {
			((Runnable) task).run();
		} else {
			try {
				task.execute();
			} catch (Exception e) {
				Helpers.athrow(e);
			}
		}
	}
}
