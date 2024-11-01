package roj.concurrent.timing;

import roj.collect.SimpleList;
import roj.concurrent.TaskHandler;
import roj.concurrent.TaskPool;
import roj.concurrent.task.ITask;
import roj.reflect.ReflectionUtils;
import roj.text.logging.Logger;
import roj.util.Helpers;

import java.util.Collection;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static roj.reflect.ReflectionUtils.u;

/**
 * 处理定时任务
 *
 * @author Roj234
 * @version 2.2
 * @since 2024/3/6 0:38
 */
public class Scheduler implements Runnable {
	private static final Logger LOGGER = Logger.getLogger("TaskSchd");

	private static volatile Scheduler defaultScheduler;
	public static Scheduler getDefaultScheduler() {
		if (defaultScheduler == null) {
			synchronized (Scheduler.class) {
				if (defaultScheduler != null) return defaultScheduler;

				defaultScheduler = new Scheduler(TaskPool.Common());
				Thread t = new Thread(defaultScheduler, "RojLib - 任务定时器");
				t.setDaemon(true);
				t.start();
			}
		}
		return defaultScheduler;
	}

	private static final class TaskHolder implements ScheduleTask {
		// root
		TaskHolder(TimingWheel wheel) {
			lock = wheel;
			prev = next = this;
		}
		TaskHolder(ITask task, long delay) {
			this.task = task;
			timeLeft = delay;
		}

		@Override
		public String toString() {
			String state;
			if (lock instanceof TimingWheel) state = "list-root";
			else if (lock instanceof TaskHolder) state = "queued";
			else state = timeLeft == 0 ? "expired" : timeLeft == -1 ? "cancelled" : "detached";
			return "ScheduleTask{state="+state+",task="+task+",lock="+lock+",timeLeft="+timeLeft+'}';
		}

		static final long OFF_LOCK = ReflectionUtils.fieldOffset(TaskHolder.class, "lock");
		static final long OFF_NEXT = ReflectionUtils.fieldOffset(TaskHolder.class, "next");
		static final long OFF_TIME_LEFT = ReflectionUtils.fieldOffset(TaskHolder.class, "timeLeft");

		Object lock;
		TaskHolder prev, next;

		ITask task;
		volatile long timeLeft;

		public ITask getTask() { return task; }
		public boolean isExpired() { return timeLeft == 0 || timeLeft == -2; }
		public boolean isCancelled() { return timeLeft < 0; }
		public boolean cancel() {
			long t;
			do {
				t = timeLeft;
				if (t < 0) break;
			} while (!u.compareAndSwapLong(this, OFF_TIME_LEFT, t, t == 0 ? -2 : -1));

			var root = (TaskHolder) lock;
			return (root != null && ((TimingWheel) root.lock).remove(root, this)) | task.cancel() | t < 0;
		}
	}

	private static final int DEPTH_SHL = 4, DEPTH_MASK = (1 << DEPTH_SHL) - 1;
	private static final class TimingWheel {
		TimingWheel(TimingWheel prev) {
			this.prev = prev;
			this.slot = prev==null ? 0 : prev.slot+1;

			tasks = new TaskHolder[1 << DEPTH_SHL];
			for (int i = 0; i < tasks.length; i++)
				tasks[i] = new TaskHolder(this);
		}

		private final TaskHolder[] tasks;
		private final int slot;
		private int clock;
		private static final long OFF_TASK_COUNT = ReflectionUtils.fieldOffset(TimingWheel.class, "taskCount");
		private volatile int taskCount;

		private final TimingWheel prev;
		// STABLE
		private volatile TimingWheel next;
		private TimingWheel getNext() {
			if (next == null) {
				synchronized (this) {
					if (next == null) next = new TimingWheel(this);
				}
			}
			return next;
		}

		@Override
		public String toString() { return next == null ? Integer.toString(clock) : next.toString()+":"+Integer.toHexString(clock); }

		final void fastForward(int ticks, Collection<TaskHolder> lazyTask, Consumer<ITask> exec) {
			int ff = ticks >>> (slot*DEPTH_SHL);
			int c = clock;
			clock = (c+ff) & DEPTH_MASK;

			if (taskCount > 0) {
				if (ff > DEPTH_MASK) ff = DEPTH_MASK;

				for (int slot = c; slot < c+ff; slot++) {
					TaskHolder root = tasks[slot&DEPTH_MASK], task = root.next;
					while (task != root) {
						TaskHolder next = task.next;
						long time = task.timeLeft;

						block:
						if (remove(root, task) && time > 0 && u.compareAndSwapLong(task, TaskHolder.OFF_TIME_LEFT, time, time = Math.max(0, time-ticks))) {
							if (time == 0) {
								time = safeApply(exec, task);
								if (time == 0) break block;

								if (u.compareAndSwapLong(task, TaskHolder.OFF_TIME_LEFT, 0, time)) lazyTask.add(task);
							} else {
								// 后序遍历
								add(prev, task);
							}
						}
						task = next;
					}
				}
			}

			if (next != null) next.fastForward(ticks, lazyTask, exec);
		}

		final void tick(Consumer<ITask> exec) {
			int c = clock;
			if (c != DEPTH_MASK) {
				clock = c+1;
			} else {
				clock = 0;

				TimingWheel p = next;
				if (p != null) p.tick(exec);
			}

			if (taskCount > 0) {
				long mask = (1L << (slot * DEPTH_SHL)) - 1;

				TaskHolder root = tasks[c], task = root.next;
				while (task != root) {
					TaskHolder next = task.next;
					long time = task.timeLeft;

					block:
					if (remove(root, task) && time > 0 && u.compareAndSwapLong(task, TaskHolder.OFF_TIME_LEFT, time, time &= mask)) {
						if (time == 0) {
							time = safeApply(exec, task);
							if (time == 0) break block;

							TimingWheel whell = this;
							while (whell.prev != null) whell = whell.prev;
							if (u.compareAndSwapLong(task, TaskHolder.OFF_TIME_LEFT, 0, fixTime(whell, time))) {
								add(this, task);
							}
						} else {
							add(prev, task);
						}
					} // else task was cancelled
					task = next;
				}
			}
		}

		static long safeApply(Consumer<ITask> exec, TaskHolder task) {
			try {
				ITask _task = task.task;
				long nextRun = _task instanceof LoopTaskWrapper loop ? loop.getNextRun() : 0;
				if (nextRun >= 0) exec.accept(_task);
				return nextRun;
			} catch (Throwable e) {
				LOGGER.error("提交任务时发生了异常", e);
				return 0;
			}
		}

		static long fixTime(TimingWheel wheel, long time) {
			assert wheel.prev == null;

			int i = 0;
			while (true) {
				int slot = (63 - Long.numberOfLeadingZeros(time)) / DEPTH_SHL;
				if (i >= slot) break;
				i++;

				time += (long) wheel.clock << (DEPTH_SHL * wheel.slot);
				wheel = wheel.next;
				if (wheel == null) break;
			}

			return time;
		}
		static void add(TimingWheel clk, TaskHolder task) {
			long time = task.timeLeft;
			if (time <= 0) return;

			int slot = (63 - Long.numberOfLeadingZeros(time)) / DEPTH_SHL;

			int delta = clk.slot - slot;
			if (delta != 0) {
				if (delta < 0) {
					while (delta++ < 0) clk = clk.getNext();
				} else {
					while (delta-- > 0) clk = clk.prev;
				}
			}

			int i = clk.clock + ((int) (time >> (DEPTH_SHL*slot)) & DEPTH_MASK) - 1;
			var root = clk.tasks[i & DEPTH_MASK];

			if (!u.compareAndSwapObject(task, TaskHolder.OFF_LOCK, null, root)) return;
			synchronized (root) {
				var next = root.next;
				task.prev = root;
				task.next = next;

				next.prev = task;
				root.next = task;
			}
			u.getAndAddInt(clk, OFF_TASK_COUNT, 1);
		}
		final boolean remove(TaskHolder root, TaskHolder task) {
			if (!u.compareAndSwapObject(task, TaskHolder.OFF_LOCK, root, null)) return false;
			synchronized (root) {
				var prev = task.prev;
				var next = task.next;

				next.prev = prev;
				prev.next = next;
			}
			u.getAndAddInt(this, OFF_TASK_COUNT, -1);
			return true;
		}

		final void collect(Collection<TaskHolder> collector) {
			if (taskCount > 0) {
				for (TaskHolder root : tasks) {
					TaskHolder task = root.next;
					while (task != root) {
						TaskHolder next = task.next;

						task.lock = task.prev = task.next = null;
						if (task.timeLeft > 0) collector.add(task);

						task = next;
					}

					root.prev = root.next = root;
				}

				taskCount = 0;
			}

			if (next != null) next.collect(collector);
		}
	}

	private static final long OFF_HEAD = ReflectionUtils.fieldOffset(Scheduler.class, "head");

	private final TimingWheel wheel = new TimingWheel(null);
	private volatile boolean stopFlag;
	private final Consumer<ITask> callback;

	private static final TaskHolder SENTIAL_HEAD_END = new TaskHolder(null);
	private volatile TaskHolder head = SENTIAL_HEAD_END;

	public Scheduler(Consumer<ITask> callback) { this.callback = callback; }
	public Scheduler(TaskHandler th) {callback = th::submit;}

	public void run() {
		int delta = 1;
		long prevTime, time = System.currentTimeMillis();

		// 这个锁是给stop用的
		synchronized (this) {
			while (!stopFlag) {
				// 系统睡眠了或者怎么了，那就别管误差了，遍历到期的任务吧，O(n)
				if (delta > 127) fastForward(delta);
				// 1（每毫秒轮询一次确实不影响性能，毕竟这是O(1)的算法，如果没有任务，仅仅是tick++
				// 2（睡久了容易产生误差，比如10000ms差了400ms，我总不能循环tick400次吧
				else while (delta-- > 0) wheel.tick(callback);

				pollNewTasks(time);

				// 奈奎斯特采样定理？不管了，反正也只有1ms的精度
				LockSupport.parkNanos(500_000L);

				prevTime = time;
				time = System.currentTimeMillis();

				delta = (int) (time - prevTime);
			}
		}
	}

	public final void tick() { wheel.tick(callback); }
	public final void fastForward(int ticks) {
		SimpleList<TaskHolder> tasks = new SimpleList<>();
		wheel.fastForward(ticks, tasks, callback);
		for (int i = 0; i < tasks.size(); i++) {
			TaskHolder task = tasks.get(i);
			long timeLeft = task.timeLeft;
			if (timeLeft > 0 && u.compareAndSwapLong(task, TaskHolder.OFF_TIME_LEFT, timeLeft, TimingWheel.fixTime(wheel, timeLeft)))
				TimingWheel.add(wheel, task);
		}
	}

	public final void pollNewTasks(long time) {
		TaskHolder h = (TaskHolder) u.getAndSetObject(this, OFF_HEAD, SENTIAL_HEAD_END);

		while (h != SENTIAL_HEAD_END) {
			TaskHolder next;
			do {
				next = (TaskHolder) u.getObject(h, TaskHolder.OFF_NEXT);
			} while (next == null || !u.compareAndSwapObject(h, TaskHolder.OFF_NEXT, next, null));

			block: {
				long timeLeft = h.timeLeft;
				// 取消，下CAS同, 因为只能取消，所以不用放在循环里
				if (timeLeft < 0) break block;

				long addTime = timeLeft - time;
				if (addTime <= 0) {
					addTime = TimingWheel.safeApply(callback, h);
					if (addTime == 0) break block;
				}

				if (!u.compareAndSwapLong(h, TaskHolder.OFF_TIME_LEFT, timeLeft, TimingWheel.fixTime(wheel, addTime)))
					break block;

				TimingWheel.add(wheel, h);
			}

			h = next;
		}
	}

	public ScheduleTask delay(ITask task, long delayMs) {
		if (delayMs < 0) throw new IllegalArgumentException("delayMs < 0");
		TaskHolder holder = new TaskHolder(task, delayMs);
		if (delayMs == 0) {
			delayMs = TimingWheel.safeApply(callback, holder);
			if (delayMs == 0) return holder; // expired
		}

		holder.timeLeft = System.currentTimeMillis()+delayMs;

		TaskHolder head = (TaskHolder) u.getAndSetObject(this, OFF_HEAD, holder);
		u.putObjectVolatile(holder, TaskHolder.OFF_NEXT, head);

		return holder;
	}
	public ScheduleTask runAsync(ITask task) { return delay(task, 1); }
	public ScheduleTask loop(ITask task, long intervalMs) { return delay(new LoopTaskWrapper(this, task, intervalMs, -1, true), 0); }
	public ScheduleTask loop(ITask task, long intervalMs, int count) { return delay(new LoopTaskWrapper(this, task, intervalMs, count, true), 0); }
	public ScheduleTask loop(ITask task, long intervalMs, int count, long delayMs) { return delay(new LoopTaskWrapper(this, task, intervalMs, count, true), delayMs); }

	public void stop(Collection<ScheduleTask> collector) {
		stopFlag = true;
		synchronized (this) { wheel.collect(Helpers.cast(collector)); }
	}
}