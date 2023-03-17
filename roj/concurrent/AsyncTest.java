package roj.concurrent;

import roj.concurrent.task.ScheduledRunnable;

/**
 * @author Roj234
 * @since 2022/2/8 9:16
 */
public final class AsyncTest extends TaskSequencer {
	public static int delay;
	public static final TaskSequencer inst;

	static {
		Thread seqWorker = new Thread(inst = new TaskSequencer());
		seqWorker.setDaemon(true);
		seqWorker.setName("异步测试工具");
		seqWorker.start();
	}

	public static void sched(Runnable task, long then) {
		inst.register(new ScheduledRunnable(0, delay += then, 1, task));
	}
}
