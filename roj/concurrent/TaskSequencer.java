package roj.concurrent;

import roj.collect.SimpleList;
import roj.concurrent.task.ITask;
import roj.concurrent.task.ScheduledTask;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 处理定时任务
 *
 * @author Roj234
 * @since 2022/2/8 8:00
 */
public class TaskSequencer implements Runnable {
	static final Comparator<Scheduled> CPR = (o1, o2) -> {
		int v = Long.compare(o1.nextRun, o2.nextRun);
		if (v == 0) v = Integer.compare(System.identityHashCode(o1), System.identityHashCode(o2));
		if (v == 0) v = o1 == o2 ? 0 : -1;
		return v;
	};

	private final List<Scheduled> add;
	private volatile PriorityQueue<Scheduled> remover;
	private PriorityQueue<Scheduled> remover1;
	private final ReentrantLock lock;
	public Thread worker;
	private boolean running;

	public TaskSequencer() {
		this.add = new SimpleList<>();
		this.remover = new PriorityQueue<>(CPR);
		this.remover1 = new PriorityQueue<>(CPR);
		this.lock = new ReentrantLock();
		this.running = true;
	}

	@Override
	public void run() {
		worker = Thread.currentThread();
		while (running) {
			long next = work();
			if (next < 0) LockSupport.park();
			else LockSupport.parkNanos(next * 1_000_000L);
		}
	}

	public void stop() {
		running = false;
		LockSupport.unpark(worker);
	}

	public void executeRest() {
		poll(true);
		PriorityQueue<Scheduled> tasks = remover;
		for (Scheduled task : tasks) {
			try {
				if (task.isCancelled()) continue;
				task.execute();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
		tasks.clear();
	}

	/**
	 * @return Minimum delay
	 */
	public long work() {
		poll(true);

		PriorityQueue<Scheduled> tasks = remover, next = remover1;
		if (tasks.isEmpty()) return -1L;

		boolean intr = Thread.interrupted();

		long time = System.currentTimeMillis();
		Iterator<Scheduled> itr = tasks.iterator();
		while (itr.hasNext()) {
			Scheduled task = itr.next();
			if (task.nextRun <= time || intr) {
				try {
					if (task.isCancelled()) continue;
					task.execute();
				} catch (Throwable e) {
					e.printStackTrace();
				}

				if (--task.count == 0) {
					task.nextRun = -1;
				} else {
					task.nextRun = task.interval + time;
					next.add(task);
				}

				time = System.currentTimeMillis();
			} else {
				next.add(task);
				break;
			}
		}

		while (itr.hasNext()) next.add(itr.next());
		tasks.clear();

		lock.lock();

		remover1 = tasks;
		remover = next;

		next.addAll(add);
		add.clear();

		try {
			if (next.isEmpty()) return -1;
			long run = next.peek().nextRun;
			return run < time ? 0 : run - time;
		} finally {
			lock.unlock();
		}
	}

	private boolean poll(boolean must) {
		if (must) lock.lock();
		else if (!lock.tryLock()) return false;

		remover.addAll(add);
		add.clear();
		lock.unlock();
		return true;
	}

	public Scheduled register(ITask task, int interval, int delay, int remain) {
		return register(new ScheduledTask(interval, delay, remain, task));
	}

	public Scheduled register(Scheduled t) {
		t.owner = this;

		lock.lock();

		add.add(t);
		Scheduled peek = remover.peek();
		if (peek == null || t.nextRun < peek.nextRun) LockSupport.unpark(worker);

		lock.unlock();
		return t;
	}
}
