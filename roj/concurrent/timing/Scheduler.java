package roj.concurrent.timing;

import org.jetbrains.annotations.Async;
import org.jetbrains.annotations.Nullable;
import roj.collect.SimpleList;
import roj.concurrent.TaskHandler;
import roj.concurrent.TaskPool;
import roj.concurrent.task.ITask;
import roj.reflect.ReflectionUtils;

import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.concurrent.locks.LockSupport;

import static roj.reflect.ReflectionUtils.u;

/**
 * 处理定时任务
 *
 * @author Roj234
 * @since 2022/2/8 8:00
 */
public class Scheduler implements Runnable {
	private static volatile Scheduler defaultScheduler;
	public static Scheduler getDefaultScheduler() {
		if (defaultScheduler == null) {
			synchronized (Scheduler.class) {
				if (defaultScheduler != null) return defaultScheduler;
				defaultScheduler = new Scheduler(TaskPool.Common());
				defaultScheduler.runNewThread("任务计划子进程");
			}
		}
		return defaultScheduler;
	}

	private static final Comparator<ScheduledTask> CPR = (o1, o2) -> {
		int v = Long.compare(o1.nextRun, o2.nextRun);
		if (v == 0) v = Integer.compare(System.identityHashCode(o1), System.identityHashCode(o2));
		if (v == 0) v = o1 == o2 ? 0 : -1;
		return v;
	};

	private static final long u_tail = ReflectionUtils.fieldOffset(Scheduler.class, "tail");
	private static final long u_owner = ReflectionUtils.fieldOffset(ScheduledTask.class, "owner");
	private volatile ScheduledTask head, tail;

	private final SimpleList<ScheduledTask> remain;
	private final PriorityQueue<ScheduledTask> timer;

	private volatile Thread worker;
	private final TaskHandler executor;

	private int timeout;

	public Scheduler(@Nullable TaskHandler executor) {
		this.remain = SimpleList.withCapacityType(16, 2);
		this.timer = new PriorityQueue<>(CPR);
		this.head = this.tail = new ScheduledTask(null, 0,0,1) { public void execute() {} };
		this.head.nextRun = -1L;
		this.executor = executor;
	}

	public void runNewThread(String name) {
		Thread t = new Thread(this, name);
		t.setDaemon(true);
		t.start();
		synchronized (this) {
			if (worker == null) {
				try {
					wait(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public void stop() {
		LockSupport.unpark(worker);
		worker = null;
	}

	@Override
	public void run() {
		Thread r = Thread.currentThread();
		synchronized (this) {
			if (worker != null) {
				throw new IllegalStateException("already running on another thread!");
			}
			worker = r;
		}

		while (worker == r) {
			long waitMs = work();
			if (waitMs < 0) LockSupport.park();
			else LockSupport.parkNanos(waitMs * 1_000_000L);
		}
	}

	private long work() {
		long time = System.currentTimeMillis();
		long startTime = time;

		boolean taskRemoved = false;
		Iterator<ScheduledTask> itr = timer.iterator();
		while (itr.hasNext()) {
			ScheduledTask task = itr.next();
			if (task.nextRun < 0) {
				taskRemoved = true;
				continue;
			}

			time = System.currentTimeMillis();
			if (time < task.nextRun) {
				remain.add(task);
				break;
			}

			try {
				if (task.scheduleNext(startTime)) {
					// 15.625ms
					if (task != head && time - task.nextRun > 16) {
						timeout ++;
					}

					remain.add(task);
				} else {
					taskRemoved = true;
				}

				executeForDebug(task);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		PriorityQueue<ScheduledTask> tasks = timer;
		if (taskRemoved) {
			while (itr.hasNext()) remain.add(itr.next());
			tasks.clear();

			for (int i = 0; i < remain.size(); i++) tasks.add(remain.get(i));
		}
		remain.clear();

		pollNewTasks();

		if (tasks.isEmpty()) return -1;
		long run = tasks.peek().nextRun - System.currentTimeMillis();
		return run < 0 ? 0 : run;
	}

	private void executeForDebug(@Async.Execute ScheduledTask task) throws Exception {
		if (executor != null && !task.forceOnScheduler()) executor.pushTask(task);
		else task.execute();
	}

	private void pollNewTasks() {
		long time = System.currentTimeMillis();

		ScheduledTask prev = head, task = prev.next;

		while (task != null) {
			if (task.nextRun >= 0) timer.add(task);
			prev = task;
			task = task.next;
		}

		task = head;
		while (task != prev) {
			ScheduledTask t = task;
			task = task.next;
			t.next = null;
		}

		head = prev;
	}

	public ScheduledTask delay(ITask task, int delayMs) { return add(new ScheduledTask(task, 0, delayMs, 1)); }
	public ScheduledTask loop(ITask task, int intervalMs) { return add(new ScheduledTask(task, intervalMs, 0, Integer.MAX_VALUE)); }
	public ScheduledTask loop(ITask task, int intervalMs, int count) { return add(new ScheduledTask(task, intervalMs, 0, count)); }
	public ScheduledTask loop(ITask task, int intervalMs, int count, int delayMs) { return add(new ScheduledTask(task, intervalMs, delayMs, count)); }

	public ScheduledTask add(@Async.Schedule ScheduledTask t) {
		if (worker == null) throw new IllegalStateException("定时器已关闭");
		if (!u.compareAndSwapObject(t, u_owner, null, this)) throw new IllegalArgumentException("任务已经注册");
		assert t.next == null;

		int j = 0;
		while (true) {
			ScheduledTask tail = this.tail;
			if (u.compareAndSwapObject(this, u_tail, tail, t)) {
				tail.next = t;
				break;
			}
			if ((++j & 15) == 0) LockSupport.parkNanos(1);
		}

		if (t.nextRun < head.nextRun || head.nextRun < 0)
			LockSupport.unpark(worker);
		return t;
	}
}
