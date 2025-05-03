package roj.concurrent;

import roj.collect.SimpleList;
import roj.reflect.ReflectionUtils;
import roj.text.logging.Logger;
import roj.util.Helpers;

import java.util.Collection;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static roj.reflect.Unaligned.U;

/**
 * 处理定时任务
 *
 * @author Roj234
 * @version 2.2
 * @since 2024/3/6 0:38
 */
public class Scheduler implements Runnable {
	private static final Logger LOGGER = Logger.getLogger("定时任务");

	private static volatile Scheduler defaultScheduler;
	public static Scheduler getDefaultScheduler() {
		if (defaultScheduler == null) {
			synchronized (Scheduler.class) {
				if (defaultScheduler != null) return defaultScheduler;

				defaultScheduler = new Scheduler(TaskPool.Common());
				Thread t = new Thread(defaultScheduler, "RojLib 定时任务");
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
		TaskHolder(Task task, long delay) {
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

		static final long NEXT_OFFSET = ReflectionUtils.fieldOffset(TaskHolder.class, "next");
		static final long TIME_OFFSET = ReflectionUtils.fieldOffset(TaskHolder.class, "timeLeft");

		Object lock;
		TaskHolder prev, next;

		Task task;
		volatile long timeLeft;
		static final long ITERATE_LOCK = 1L, REMOVE_LOCK = 2L;

		public Task getTask() { return task; }
		public boolean isExpired() { return timeLeft == 0 || timeLeft == -2; }
		public boolean isCancelled() { return timeLeft < 0; }
		public boolean cancel() {
			boolean taskCancelled;

			long t;
			for(;;) {
				t = timeLeft;
				if (t < 0) return true;

				if (U.compareAndSwapLong(this, TIME_OFFSET, t, t == 0 ? -2 : -1)) {
					taskCancelled = task.cancel();
					break;
				}
			}

			boolean removeFromTimer = false;
			for(;;) {
				var root = (TaskHolder) lock;
				if (root == null) break;

				// 拿到root的删除锁
				while (!U.compareAndSwapLong(root, TIME_OFFSET, 0, REMOVE_LOCK));

				// critical zone
				if (prev != null) {
					prev.next = next;
					next.prev = prev;
					removed();
					removeFromTimer = true;
				} else {
					// 在拿到锁之前，这个任务已经提交了，或者进入上一层时间轮
					// 然而，因为timeLeft已经被设为负值，它并不会被add
				}
				// critical zone

				root.timeLeft = 0;
			}

			return taskCancelled | removeFromTimer;
		}

		// 这个方法只会在任务计划线程调用
		boolean add(TaskHolder root) {
			if (isCancelled()) return false;

			boolean locked;
			while (true) {
				var r = root.timeLeft;
				if (r == ITERATE_LOCK) {
					locked = false;
					break;
				}
				if (r == 0 && U.compareAndSwapLong(root, TIME_OFFSET, 0, ITERATE_LOCK)) {
					locked = true;
					break;
				}
			}

			// critical zone
			boolean doAdd = !isCancelled();
			if (doAdd) {
				var rootNext = root.next;

				this.prev = root;
				this.next = rootNext;

				rootNext.prev = this;
				root.next = this;

				this.lock = root;
			}
			// critical zone

			if (locked) root.timeLeft = 0;
			return doAdd;
		}

		void removed() {
			prev = null;
			next = null;
			lock = null;
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
		private int tick;

		private final TimingWheel prev;
		// @Stable
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
		public String toString() { return next == null ? Integer.toString(tick) : next.toString()+":"+Integer.toHexString(tick); }

		final void fastForward(int ticks, Collection<TaskHolder> lazyTask, Consumer<Task> exec) {
			int ff = ticks >>> (slot*DEPTH_SHL);
			int t = tick;
			tick = (t+ff) & DEPTH_MASK;
			if (ff > DEPTH_MASK) ff = DEPTH_MASK;

			for (int slot = t; slot < t+ff; slot++) {
				var root = tasks[slot&DEPTH_MASK];
				while (!U.compareAndSwapLong(root, TaskHolder.TIME_OFFSET, 0, TaskHolder.ITERATE_LOCK));
				var task = root.next;
				root.next = root.prev = root;

				while (task != root) {
					var next = task.next;
					long time = task.timeLeft;
					task.removed();

					TaskWasCancelled:
					if (time > 0 && U.compareAndSwapLong(task, TaskHolder.TIME_OFFSET, time, time = Math.max(0, time-ticks))) {
						if (time == 0) {
							time = safeApply(exec, task);
							if (time <= 0) break TaskWasCancelled;

							if (U.compareAndSwapLong(task, TaskHolder.TIME_OFFSET, 0, time)) lazyTask.add(task);
						} else {
							add(prev, task);
						}
					}

					task = next;
				}

				root.timeLeft = 0;
			}

			if (next != null) next.fastForward(ticks, lazyTask, exec);
		}

		final void tick(Consumer<Task> exec) {
			int t = tick;
			if (t != DEPTH_MASK) {
				tick = t+1;
			} else {
				tick = 0;

				TimingWheel p = next;
				if (p != null) p.tick(exec);
			}

			var mask = (1L << (slot * DEPTH_SHL)) - 1;

			var root = tasks[t];
			//Lock
			while (!U.compareAndSwapLong(root, TaskHolder.TIME_OFFSET, 0, TaskHolder.ITERATE_LOCK));
			var task = root.next;
			//Clear, for add
			root.next = root.prev = root;

			while (task != root) {
				var next = task.next;
				long time = task.timeLeft;
				task.removed();

				TaskWasCancelled:
				if (time > 0 && U.compareAndSwapLong(task, TaskHolder.TIME_OFFSET, time, time &= mask)) {
					if (time == 0) {
						time = safeApply(exec, task);
						if (time <= 0) break TaskWasCancelled;

						TimingWheel wheel = this;
						while (wheel.prev != null) wheel = wheel.prev;
						if (U.compareAndSwapLong(task, TaskHolder.TIME_OFFSET, 0, fixTime(wheel, time))) {
							add(this, task);
						}
					} else {
						// 后序遍历
						add(prev, task);
					}
				}

				task = next;
			}

			root.timeLeft = 0;
		}

		static long safeApply(Consumer<Task> exec, TaskHolder task) {
			try {
				Task _task = task.task;
				long nextRun = _task instanceof LoopTask loop ? loop.getNextRun() : 0;
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

				time += (long) wheel.tick << (DEPTH_SHL * wheel.slot);
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

			int i = clk.tick + ((int) (time >> (DEPTH_SHL*slot)) & DEPTH_MASK) - 1;
			var root = clk.tasks[i & DEPTH_MASK];
			task.add(root);
		}

		final void collect(Collection<TaskHolder> collector) {
			for (TaskHolder root : tasks) {
				while (!U.compareAndSwapLong(root, TaskHolder.TIME_OFFSET, 0, TaskHolder.ITERATE_LOCK));
				var task = root.next;
				root.prev = root.next = root;
				while (task != root) {
					if (task.timeLeft > 0) collector.add(task);

					var next = task.next;
					task.removed();
					task = next;
				}

				root.timeLeft = 0;
			}

			if (next != null) next.collect(collector);
		}
	}

	private static final long OFF_HEAD = ReflectionUtils.fieldOffset(Scheduler.class, "head");

	private final TimingWheel wheel = new TimingWheel(null);
	private volatile boolean stopped;
	private final Consumer<Task> executor;

	private static final TaskHolder SENTIAL_HEAD_END = new TaskHolder(null);
	private volatile TaskHolder head = SENTIAL_HEAD_END;

	public Scheduler(Consumer<Task> executor) {this.executor = executor;}
	public Scheduler(TaskExecutor th) {executor = th::submit;}

	public void run() {
		int delta = 1;
		long prevTime, time = System.currentTimeMillis();

		// 这个锁是给stop用的
		synchronized (this) {
			while (!stopped) {
				try {
					// 系统睡眠了或者怎么了，那就别管误差了，遍历到期的任务吧，O(n)
					if (delta > 127) fastForward(delta);
					// 1（每毫秒轮询一次确实不影响性能，毕竟这是O(1)的算法，如果没有任务，仅仅是tick++
					// 2（睡久了容易产生误差，比如10000ms差了400ms，我总不能循环tick400次吧
					else while (delta-- > 0) wheel.tick(executor);

					pollNewTasks(time);
				} catch (Throwable e) {
					LOGGER.error("遇到了异常", e);
				}

				// 奈奎斯特采样定理？不管了，反正也只有1ms的精度
				LockSupport.parkNanos(500_000L);

				prevTime = time;
				time = System.currentTimeMillis();

				delta = (int) (time - prevTime);
			}
		}
	}

	public final void tick() { wheel.tick(executor); }
	public final void fastForward(int ticks) {
		SimpleList<TaskHolder> tasks = new SimpleList<>();
		wheel.fastForward(ticks, tasks, executor);
		for (int i = 0; i < tasks.size(); i++) {
			TaskHolder task = tasks.get(i);
			long timeLeft = task.timeLeft;
			if (timeLeft > 0 && U.compareAndSwapLong(task, TaskHolder.TIME_OFFSET, timeLeft, TimingWheel.fixTime(wheel, timeLeft)))
				TimingWheel.add(wheel, task);
		}
	}

	public final void pollNewTasks(long time) {
		TaskHolder h = (TaskHolder) U.getAndSetObject(this, OFF_HEAD, SENTIAL_HEAD_END);

		while (h != SENTIAL_HEAD_END) {
			TaskHolder next;
			do {
				next = (TaskHolder) U.getObject(h, TaskHolder.NEXT_OFFSET);
			} while (next == null || !U.compareAndSwapObject(h, TaskHolder.NEXT_OFFSET, next, null));

			block: {
				long timeLeft = h.timeLeft;
				// 取消，下CAS同, 因为只能取消，所以不用放在循环里
				if (timeLeft <= 0) break block;

				long addTime = timeLeft - time;
				if (addTime <= 0) {
					addTime = TimingWheel.safeApply(executor, h);
					if (addTime == 0) {
						h.timeLeft = 0;
						break block;
					}
				}

				if (!U.compareAndSwapLong(h, TaskHolder.TIME_OFFSET, timeLeft, TimingWheel.fixTime(wheel, addTime)))
					break block;

				TimingWheel.add(wheel, h);
			}

			h = next;
		}
	}

	public ScheduleTask delay(Task task, long delayMs) {
		if (delayMs < 0) throw new IllegalArgumentException("delayMs < 0");
		TaskHolder holder = new TaskHolder(task, delayMs);
		if (delayMs == 0) {
			delayMs = TimingWheel.safeApply(executor, holder);
			if (delayMs == 0) return holder; // expired
		}

		holder.timeLeft = System.currentTimeMillis()+delayMs;

		TaskHolder head = (TaskHolder) U.getAndSetObject(this, OFF_HEAD, holder);
		U.putObjectVolatile(holder, TaskHolder.NEXT_OFFSET, head);

		return holder;
	}
	public ScheduleTask runAsync(Task task) { return delay(task, 1); }
	public ScheduleTask loop(Task task, long intervalMs) { return delay(new LoopTask(this, task, intervalMs, -1, true), 0); }
	public ScheduleTask loop(Task task, long intervalMs, int count) { return delay(new LoopTask(this, task, intervalMs, count, true), 0); }
	public ScheduleTask loop(Task task, long intervalMs, int count, long delayMs) { return delay(new LoopTask(this, task, intervalMs, count, true), delayMs); }

	public void stop(Collection<ScheduleTask> collector) {
		stopped = true;
		synchronized (this) { wheel.collect(Helpers.cast(collector)); }
	}
}