package roj.concurrent;

import org.jetbrains.annotations.Async;
import roj.collect.IntMap;
import roj.collect.MyHashSet;
import roj.collect.RingBuffer;
import roj.concurrent.task.ITask;
import roj.util.Helpers;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static roj.reflect.FieldAccessor.u;

public class TaskPool implements TaskHandler {
	@FunctionalInterface
	public interface MyThreadFactory {
		TaskPool.ExecutorImpl get(TaskPool pool);
	}

	@FunctionalInterface
	public interface RejectPolicy {
		void onReject(TaskPool pool, ITask task);
	}

	private final MyHashSet<ExecutorImpl> threads = new MyHashSet<>();
	private final MyThreadFactory factory;
	private RejectPolicy policy;

	final int core, max, newThr, idleTime;
	private volatile int running, parking;
	private long prevStop;

	static {
		try {
			RUNNING_OFFSET = u.objectFieldOffset(TaskPool.class.getDeclaredField("running"));
			PARKING_OFFSET = u.objectFieldOffset(TaskPool.class.getDeclaredField("parking"));
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		}
	}
	private static long RUNNING_OFFSET, PARKING_OFFSET;

	final ReentrantLock lock = new ReentrantLock();
	final Condition noFull = lock.newCondition();
	final RingBuffer<ITask> tasks;
	final TransferQueue<ITask> fastPath = new LinkedTransferQueue<>();

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
		this(coreThreads, maxThreads, threshold, 60000, "pool-" + poolId.getAndIncrement() + "-by" + Thread.currentThread().getId() + "-");
	}

	public static TaskPool CpuMassive() { return CpuMassiveHolder.P; }
	private static final class CpuMassiveHolder {
		static final TaskPool P = new TaskPool(1, Runtime.getRuntime().availableProcessors(), 0, 60000, "Cpu任务-");
	}

	public static TaskPool ParallelPool() {
		return new TaskPool(0, Runtime.getRuntime().availableProcessors()+1, 128);
	}

	@Override
	public void pushTask(@Async.Schedule ITask task) {
		if (task.isCancelled()) return;

		int len = running;
		if (len <= 0) {
			if (len < 0) throw new RejectedExecutionException("TaskPool was shutdown.");

			newWorker();
		}

		// 等待立即结束的短时任务
		try {
			if (fastPath.tryTransfer(task, 10, TimeUnit.MICROSECONDS)) return;
		} catch (InterruptedException ignored) {}

		if (task.isCancelled()) return;

		len = running;
		if (len < 0) throw new RejectedExecutionException("TaskPool was shutdown.");
		else {
			if (len < core ||
				len < max &&
					(newThr == 0 ||
					(newThr < 0 ? tasks.size()/len : tasks.size()) > Math.abs(newThr))
			) {
				newWorker();
			}
		}

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

		main:
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
						// noinspection all
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
		if (u.compareAndSwapInt(this, RUNNING_OFFSET, r, r+1)) {
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
		while (pool.tasks.remaining() == 0) {
			pool.noFull.awaitUninterruptibly();
			if (pool.running < 0) throw new RejectedExecutionException("TaskPool was shutdown.");

			if (!pool.fastPath.tryTransfer(task)) {
				pool.tasks.ringAddLast(task);
			}
		}
	}
	public static void newThreadPolicy(TaskPool pool, ITask task) { new ImmediateExecutor(task).start(); }

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

		tasks.clear();

		while (fastPath.tryTransfer(Helpers.cast(IntMap.UNDEFINED)));
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
			while ((!tasks.isEmpty() || running != parking) && running > 0) {
				try {
					wait(0);
				} catch (InterruptedException e) {
					break;
				}
			}
		}
	}
	public boolean awaitFinish(long timeout)  {
		long time = System.currentTimeMillis() + timeout;
		synchronized (this) {
			while ((!tasks.isEmpty() || running != parking) && running > 0) {
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
		return "TaskPool{" + ", range=[" + core + ',' + max + "], lock=" + lock + '}';
	}

	public static void yield() {
		Thread t = Thread.currentThread();
		if (t instanceof ExecutorImpl) {
			// todo
		}
	}

	public class ExecutorImpl extends FastLocalThread {
		protected ExecutorImpl() { setDaemon(true); }
		protected ExecutorImpl(String name) {
			setName(name);
			setDaemon(true);
		}
		protected ExecutorImpl(ThreadGroup tg, String name) {
			super(tg, name);
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
						executeIdea(task);
						task.execute();
						if (task.continueExecuting()) pushTask(task);
					}
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}

			synchronized (threads) { threads.remove(this); }
		}

		private final void executeIdea(@Async.Execute ITask task) {}
	}
}