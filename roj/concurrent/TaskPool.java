package roj.concurrent;

import org.jetbrains.annotations.Async;
import roj.collect.IntMap;
import roj.collect.MyHashSet;
import roj.collect.RingBuffer;
import roj.concurrent.task.ITask;
import roj.reflect.ReflectionUtils;
import roj.text.logging.Logger;
import roj.util.Helpers;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static roj.reflect.ReflectionUtils.u;

public class TaskPool implements TaskHandler {
	@FunctionalInterface
	public interface MyThreadFactory {
		TaskPool.ExecutorImpl get(TaskPool pool);
	}

	@FunctionalInterface
	public interface RejectPolicy {
		void onReject(TaskPool pool, ITask task);
	}

	final MyHashSet<ExecutorImpl> threads = new MyHashSet<>();
	private final MyThreadFactory factory;
	private RejectPolicy policy;
	private Thread.UncaughtExceptionHandler exceptionHandler;

	final int core, max, newThr, idleTime;
	private volatile int running, parking;
	private volatile long prevStop;

	private static final long
		RUNNING_OFFSET = ReflectionUtils.fieldOffset(TaskPool.class, "running"),
		PARKING_OFFSET = ReflectionUtils.fieldOffset(TaskPool.class, "parking");

	final ReentrantLock lock = new ReentrantLock();
	final Condition noFull = lock.newCondition();
	final RingBuffer<ITask> tasks;
	final TransferQueue<Object> fastPath = new LinkedTransferQueue<>();

	public TaskPool(int coreThreads, int maxThreads, int newThreshold, int rejectThreshold, int stopTimeout, MyThreadFactory factory) {
		this.core = coreThreads;
		this.max = maxThreads;
		this.newThr = newThreshold;
		this.idleTime = stopTimeout;
		this.factory = factory;

		tasks = new RingBuffer<>(Math.min(64, rejectThreshold), rejectThreshold);
	}

	public TaskPool(int coreThreads, int maxThreads, int newThreshold, int stopTimeout, String namePrefix) {
		this.core = coreThreads;
		this.max = maxThreads;
		if (coreThreads > maxThreads) throw new IllegalArgumentException("coreThreads > maxThreads");
		this.newThr = newThreshold;
		this.idleTime = stopTimeout;
		if (stopTimeout < 100) throw new IllegalArgumentException("stopTimeout < 100");
		this.factory = new PrefixFactory(namePrefix);

		tasks = new RingBuffer<>(64, 99999999);
	}

	private static final AtomicInteger poolId = new AtomicInteger();

	public TaskPool(int coreThreads, int maxThreads, int threshold) {
		this(coreThreads, maxThreads, threshold, 60000, "pool-"+poolId.getAndIncrement()+"-by"+Thread.currentThread().getId() + "-");
	}

	public static TaskPool Common() { return CommonHolder.P; }
	private static final class CommonHolder {
		static final TaskPool P = new TaskPool(1, Runtime.getRuntime().availableProcessors(), 0, 60000, "Cpu任务-");
	}

	public static TaskPool MaxThread(int threadCount, String prefix) { return new TaskPool(0, threadCount, 1, 16, 60000, new PrefixFactory(prefix)); }
	public static TaskPool MaxThread(int threadCount, MyThreadFactory factory) { return new TaskPool(0, threadCount, 1, 16, 60000, factory); }
	public static TaskPool MaxSize(int rejectThreshold, String prefix) { return new TaskPool(0, Runtime.getRuntime().availableProcessors(), 0, rejectThreshold, 60000, new PrefixFactory(prefix)); }

	@Override
	public void pushTask(@Async.Schedule ITask task) {
		if (task.isCancelled()) return;

		int len = running;
		// move throw into newWorker()
		if (len <= 0) newWorker();

		// 等待立即结束的短时任务
		try {
			if (fastPath.tryTransfer(task, 10, TimeUnit.MICROSECONDS)) return;
		} catch (InterruptedException ignored) {}

		len = running;
		if (len < core ||
			len < max &&
				(newThr == 0 ||
					(newThr < 0 ? tasks.size()/len : tasks.size()) > Math.abs(newThr))
		) newWorker();

		lock.lock();
		try {
			if (tasks.remaining() == 0) {
				if (policy != null) {
					policy.onReject(this, task);
				} else {
					throwPolicy(this, task);
				}
			} else {
				tasks.ringAddLast(task);
			}
		} finally {
			lock.unlock();
		}
	}

	private ITask pollTask() {
		boolean timeout = false;

		for(;;) {
			// shutdown
			if (running < 0) {
				int r;
				do {
					r = running;
				} while (!u.compareAndSwapInt(this, RUNNING_OFFSET, r, r+1));

				if (r == -2) {
					synchronized (threads) { threads.notifyAll(); }
				}
				return null;
			}

			if (running > core &&
				timeout &&
				System.currentTimeMillis() - prevStop >= idleTime) {

				int r = running;
				if (u.compareAndSwapInt(this, RUNNING_OFFSET, r, r-1)) {
					prevStop = System.currentTimeMillis();

					synchronized (this) { notifyAll(); }
					return null;
				}
			}

			timeout = false;
			try {
				if (!tasks.isEmpty()) {
					lock.lock();
					try {
						while (!tasks.isEmpty()) {
							ITask task = tasks.removeFirst();
							if (!task.isCancelled()) return task;
						}
					} finally {
						noFull.signalAll();
						lock.unlock();
					}
				}

				int r;
				do {
					r = parking;
				} while (!u.compareAndSwapInt(this, PARKING_OFFSET, r, r+1));

				synchronized (this) { notifyAll(); }

				try {
					// original noEmpty.await
					Object task = fastPath.poll(idleTime, TimeUnit.MILLISECONDS);
					if (task == null || task == IntMap.UNDEFINED) {
						timeout = true;
					} else {
						ITask tt = (ITask) task;
						if (!tt.isCancelled()) return tt;
					}
				} finally {
					do {
						r = parking;
					} while (!u.compareAndSwapInt(this, PARKING_OFFSET, r, r-1));
				}
			} catch (InterruptedException ignored) {}
		}
	}

	private void newWorker() {
		int r = running;
		if (r < 0) throw new IllegalStateException("TaskPool was shutdown.");
		if (u.compareAndSwapInt(this, RUNNING_OFFSET, r, r+1)) {
			prevStop = System.currentTimeMillis();

			ExecutorImpl t = factory.get(this);
			synchronized (threads) { threads.add(t); }
			t.start();
		}
	}

	public void setRejectPolicy(RejectPolicy policy) { this.policy = policy; }

	public static void throwPolicy(TaskPool pool, ITask task) { throw new RejectedExecutionException("Too many tasks pending"); }
	public static void executePolicy(TaskPool pool, ITask task) {
		try {
			task.execute();
		} catch (Exception e) {
			if (!(e instanceof ExecutionException)) {
				e = new ExecutionException(e);
			}
			Helpers.athrow(e);
		}
	}
	public static void waitPolicy(TaskPool pool, ITask task) {
		while (true) {
			if (pool.fastPath.tryTransfer(task)) break;
			if (pool.tasks.remaining() > 0) {
				pool.tasks.ringAddLast(task);
				break;
			}

			pool.noFull.awaitUninterruptibly();
			if (pool.running < 0) throw new RejectedExecutionException("TaskPool was shutdown.");
		}
	}

	@Override
	public void clearTasks() {
		lock.lock();
		tasks.clear();
		noFull.signalAll();
		lock.unlock();
	}

	public void shutdown() {
		lock.lock();

		int r;
		do {
			r = running;
		} while (!u.compareAndSwapInt(this, RUNNING_OFFSET, r, -r-1));

		synchronized (this) { notifyAll(); }

		tasks.clear();

		while (fastPath.tryTransfer(IntMap.UNDEFINED));
		noFull.signalAll();

		lock.unlock();
	}

	public boolean working() {
		return running >= 0;
	}

	public Thread[] threads() {
		synchronized (threads) {
			return threads.toArray(new Thread[threads.size()]);
		}
	}

	public int taskPending() { return tasks.size(); }

	public void awaitFinish() {
		synchronized (this) {
			while (parking < running) {
				try {
					wait(1);
				} catch (InterruptedException e) {
					break;
				}
			}
		}
	}
	public boolean awaitFinish(long timeout)  {
		long time = System.currentTimeMillis() + timeout;
		synchronized (this) {
			while (parking < running) {
				long dt = time - System.currentTimeMillis();
				if (dt < 0) return false;
				try {
					wait(dt);
				} catch (InterruptedException e) {
					break;
				}
			}
		}
		return true;
	}

	public void awaitShutdown() throws InterruptedException {
		synchronized (threads) {
			while (running != -1) threads.wait();
		}
	}

	@Override
	public String toString() {
		return "TaskPool{" + ", range=[" + core + ',' + max + "], lock="+lock+'}';
	}

	public class ExecutorImpl extends FastLocalThread {
		public ExecutorImpl() { setDaemon(true); }
		public ExecutorImpl(String name) {
			setName(name);
			setDaemon(true);
		}

		@Override
		public void run() {
			while (true) {
				ITask task = pollTask();
				if (task == null) break;

				try {
					if (!task.isCancelled()) {
						executeForDebug(task);
						if (task.repeating()) pushTask(task);
					}
				} catch (Throwable e) {
					if (exceptionHandler != null) exceptionHandler.uncaughtException(this, e);
					else Logger.getLogger("TaskPool@"+TaskPool.this.hashCode()).error(e);
				}
			}

			synchronized (threads) { threads.remove(this); }
		}

		private void executeForDebug(@Async.Execute ITask task) throws Exception { task.execute(); }
	}
}