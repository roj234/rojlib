package roj.concurrent.timing;

import org.jetbrains.annotations.Async;
import org.jetbrains.annotations.Nullable;
import roj.collect.SimpleList;
import roj.concurrent.TaskHandler;
import roj.concurrent.TaskPool;
import roj.concurrent.task.ITask;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
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
				defaultScheduler.runNewThread("定时任务调度器");
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

	static {
		try {
			TAIL_OFFSET = u.objectFieldOffset(Scheduler.class.getDeclaredField("tail"));
		} catch (NoSuchFieldException ignored) {}
	}
	private static long TAIL_OFFSET;
	private volatile ScheduledTask head, tail;

	private final SimpleList<ScheduledTask> remain;
	private final PriorityQueue<ScheduledTask> timer;

	private volatile Thread worker;
	private final TaskHandler executor;

	private Scheduler parent, child;
	private final int maxSubSchedulers;

	public Scheduler(@Nullable TaskHandler executor) {
		this(executor, 1);
	}
	public Scheduler(@Nullable TaskHandler executor, int maxHelperThreadCount) {
		this.remain = SimpleList.withCapacityType(16, 2);
		this.timer = new PriorityQueue<>(CPR);
		this.head = this.tail = new ScheduledTask(null, 0,0,1) { public void execute() {} };
		this.head.nextRun = Long.MAX_VALUE;
		this.executor = executor;
		this.maxSubSchedulers = maxHelperThreadCount;
	}

	@Override
	public void run() {
		worker = Thread.currentThread();
		while (worker != null) {
			long waitMs = work();
			if (waitMs < 0) {
				if (parent != null) {
					synchronized (parent.timer) {
						if (head == tail) {
							parent.child = null;
							System.out.println("shutting down helper thread " + Thread.currentThread().getName());
							break;
						}
					}
				}
				LockSupport.park();
			} else {
				LockSupport.parkNanos(waitMs * 1_000_000L);
			}
		}
	}

	private long work() {
		loadNewlyAddedTask();
		PriorityQueue<ScheduledTask> tasks = timer;

		long time = System.currentTimeMillis();
		long startTime = time;

		boolean dirty = false;
		Iterator<ScheduledTask> itr = tasks.iterator();
		while (itr.hasNext()) {
			ScheduledTask task = itr.next();
			if (task.nextRun < 0) {
				dirty = true;
				continue;
			}

			time = System.currentTimeMillis();
			if (time < task.nextRun) {
				remain.add(task);
				break;
			}

			try {
				boolean timeout = false;
				if (task.schedule(startTime)) {
					// 15.625ms
					if (maxSubSchedulers > 0 && task != head && time - task.nextRun > 16) {
						dirty = timeout = true;
					} else remain.add(task);
				} else dirty = true;

				executeForDebug(task);

				if (timeout) {
					synchronized (tasks) {
						if (child == null) {
							child = new Scheduler(executor, maxSubSchedulers - 1);
							child.parent = this;
							child.runNewThread(Thread.currentThread().getName() + " - 副本");
						}

						child.add(task);
					}
				}
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		if (dirty) {
			while (itr.hasNext()) remain.add(itr.next());
			tasks.clear();

			for (int i = 0; i < remain.size(); i++) tasks.add(remain.get(i));
		}
		remain.clear();

		loadNewlyAddedTask();

		if (tasks.isEmpty()) return -1;
		long run = tasks.peek().nextRun - System.currentTimeMillis();
		return run < 0 ? 0 : run;
	}

	private void executeForDebug(@Async.Execute ScheduledTask task) throws Exception {
		if (executor != null && !task.forceOnScheduler()) executor.pushTask(task);
		else task.execute();
	}

	public void runNewThread(String name) {
		Thread timer = new Thread(this);
		timer.setName(name);
		timer.setDaemon(true);
		timer.start();
		worker = timer;
	}

	public void stop() {
		LockSupport.unpark(worker);
		worker = null;
	}

	public List<ScheduledTask> getTasks() {
		loadNewlyAddedTask();
		SimpleList<ScheduledTask> s = new SimpleList<>(timer);
		timer.clear();
		return s;
	}

	private void loadNewlyAddedTask() {
		long time = System.currentTimeMillis();

		ScheduledTask prevTask = head;
		ScheduledTask task = prevTask.next;

		remain.clear();
		while (task != null) {
			if (task.nextRun > 0) {
				if (time < task.nextRun) {
					remain.add(task);
				} else {
					try {
						if (task.schedule(time)) remain.add(task);

						if (executor != null && !task.forceOnScheduler()) executor.pushTask(task);
						else {
							task.execute();
							time = System.currentTimeMillis();
						}
					} catch (Throwable e) {
						e.printStackTrace();
					}
				}

			}

			prevTask = task;
			task = task.next;
		}
		timer.addAll(remain);
		remain.clear();

		task = head;
		while (task != prevTask) {
			ScheduledTask task1 = task;
			task = task.next;
			task1.next = null;
		}

		head = prevTask;
	}

	public ScheduledTask executeLater(ITask task, int delayMs) { return add(new ScheduledTask(task, 0, delayMs, 1)); }
	public ScheduledTask executeTimer(ITask task, int intervalMs) { return add(new ScheduledTask(task, intervalMs, 0, Integer.MAX_VALUE)); }
	public ScheduledTask executeTimer(ITask task, int intervalMs, int count) { return add(new ScheduledTask(task, intervalMs, 0, count)); }
	public ScheduledTask executeTimer(ITask task, int intervalMs, int count, int delayMs) { return add(new ScheduledTask(task, intervalMs, delayMs, count)); }

	public ScheduledTask add(@Async.Schedule ScheduledTask t) {
		if (worker == null) throw new IllegalStateException("定时器已关闭");
		t.owner = this;
		t.next = null;

		int j = 0;
		while (true) {
			ScheduledTask tail = this.tail;
			if (u.compareAndSwapObject(this, TAIL_OFFSET, tail, t)) {
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
