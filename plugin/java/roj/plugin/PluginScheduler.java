package roj.plugin;

import roj.collect.HashSet;
import roj.collect.Hasher;
import roj.concurrent.*;

/**
 * @author Roj234
 * @since 2025/09/04 00:41
 */
public final class PluginScheduler extends Timer implements Executor {
	final Plugin plugin;
	final HashSet<Cancellable> userTasks = new HashSet<>(Hasher.identity());

	private final Timer timer;

	PluginScheduler(Plugin plugin, Timer timer) {
		super(null);
		this.plugin = plugin;
		this.timer = timer;
	}

	@Override
	public void execute(Runnable task) {
		PAsyncTask wrapper = new PAsyncTask(task);
		synchronized (userTasks) { userTasks.add(wrapper); }
		TaskPool.common().execute(wrapper);
	}

	@Override
	public void run() {
		throw new UnsupportedOperationException("Plugin should not call this method");
	}

	@Override
	public TimerTask delay(Runnable task, long delayMs) {
		TimerTask delay = timer.delay(task, delayMs);
		synchronized (userTasks) {
			userTasks.add(delay);
		}
		return delay;
	}

	@Override
	public TimerTask loop(Runnable task, long period, int repeat, long delay) {
		return delay(new PPeriodicTask(task, period, repeat), delay);
	}

	@Override
	public void cancel() {
		synchronized (userTasks) {
			for (var task : userTasks) task.cancel();
			userTasks.clear();
		}
	}

	private final class PAsyncTask implements Runnable, Cancellable {
		private final Runnable task;

		public PAsyncTask(Runnable task) {
			this.task = task;
		}

		public boolean isCancelled() {
			return task instanceof Cancellable cancellable && cancellable.isCancelled();
		}

		public boolean cancel(boolean mayInterruptIfRunning) {
			return task instanceof Cancellable cancellable && cancellable.cancel(mayInterruptIfRunning);
		}

		@Override
		public void run() {
			long start = System.currentTimeMillis();

			try {
				task.run();
			} finally {
				synchronized (userTasks) {
					userTasks.remove(this);
				}
			}

			start = System.currentTimeMillis() - start;
			if (start > 1000) plugin.getLogger().warn("任务{}花费了太长的时间", task);
		}
	}

	private final class PPeriodicTask extends PeriodicTask {
		PPeriodicTask(Runnable task, long interval, int repeat) {
			super(task, interval, repeat, true);
		}

		@Override
		public long getNextRun() {
			long run = super.getNextRun();
			if (run == 0 && repeat == 0) {
				synchronized (userTasks) {
					userTasks.remove(handle);
				}
			}
			return run;
		}
	}
}
