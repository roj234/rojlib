package roj.concurrent;

import org.jetbrains.annotations.Async;
import roj.collect.ArrayList;
import roj.collect.HashSet;
import roj.collect.IntMap;
import roj.collect.RingBuffer;
import roj.reflect.Unaligned;
import roj.text.logging.Logger;

import java.util.List;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static roj.reflect.Unaligned.U;

public class TaskPool implements ExecutorService {
	@FunctionalInterface
	public interface MyThreadFactory {
		PoolThread get(TaskPool pool);
	}

	@FunctionalInterface
	public interface RejectPolicy {
		void onReject(TaskPool pool, Runnable task);
	}

	final Object threadLock = new Object();
	final HashSet<PoolThread> threads = new HashSet<>();
	private final MyThreadFactory factory;
	private RejectPolicy policy;
	private Thread.UncaughtExceptionHandler exceptionHandler = LOG_HANDLER;

	final int core, max, newThr, idleTime;
	private volatile int running;
	private volatile long prevStop;

	private static final long RUNNING = Unaligned.fieldOffset(TaskPool.class, "running");

	final ReentrantLock queueLock = new ReentrantLock();
	final Condition noFull = queueLock.newCondition();
	final RingBuffer<Runnable> tasks;
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
		this.factory = namedPrefixFactory(namePrefix);

		// large enough, but (might) will not cause OOM
		tasks = new RingBuffer<>(64, 200000);
	}

	private static final AtomicInteger poolId = new AtomicInteger();

	public TaskPool(int coreThreads, int maxThreads, int threshold) {
		this(coreThreads, maxThreads, threshold, 60000, "pool-"+poolId.getAndIncrement()+"-by"+Thread.currentThread().getId()+"-");
	}

	public static TaskPool common() { return Common.P; }
	private static final class Common {
		static final TaskPool P = new TaskPool(1, Integer.getInteger("roj.cpuPoolSize", Runtime.getRuntime().availableProcessors()), 0, 60000, "RojLib 线程池 #");
	}

	public static TaskPool newFixed(String prefix) { return newFixed(Runtime.getRuntime().availableProcessors(), prefix); }
	public static TaskPool newFixed(int threadCount, String prefix) { return newFixed(threadCount, namedPrefixFactory(prefix)); }
	public static TaskPool newFixed(int threadCount, MyThreadFactory factory) {
		TaskPool pool = new TaskPool(threadCount, threadCount, 0, Math.min(threadCount*2, 256), 60000, factory);
		pool.setRejectPolicy(TaskPool::waitPolicy);
		return pool;
	}

	public void setExceptionHandler(Thread.UncaughtExceptionHandler handler) {exceptionHandler = handler;}

	@Override
	public TaskGroup newGroup() {
		return newGroup(Thread.currentThread() instanceof PoolThread impl && impl.pool() == this);
	}

	@Override
	public void execute(@Async.Schedule Runnable task) {
		if (isCancelled(task)) return;

		int len = running;
		// move throw into newWorker()
		if (len <= 0) newWorker();

		// 等待立即结束的短任务
		if (fastPath.tryTransfer(task)) return;

		len = running;
		if (len < core ||
			len < max &&
				(newThr == 0 ||
					(newThr < 0 ? tasks.size()/len : tasks.size()) > Math.abs(newThr))
		) newWorker();

		queueLock.lock();
		try {
			if (len < 0) throw new RejectedExecutionException(this+" was shutdown.");
			if (!tasks.offerLast(task)) {
				if (policy == null) throwPolicy(this, task);
				else policy.onReject(this, task);
			}
		} finally {
			queueLock.unlock();
		}
	}

	private static boolean isCancelled(Runnable task) {return task instanceof Cancellable cancellable && cancellable.isCancelled();}

	final Runnable pollTask() {
		boolean timeout = false;

		for(;;) {
			if (!tasks.isEmpty()) {
				queueLock.lock();
				try {
					while (!tasks.isEmpty()) {
						var task = tasks.removeFirst();
						if (!isCancelled(task)) return task;
					}
				} finally {
					noFull.signal();
					queueLock.unlock();
				}
			}

			Object task = fastPath.poll();
			if (task == null) {
				int r = running;
				if (r < 0) {// shutdown
					if (U.getAndAddInt(this, RUNNING, 1) == -2) {
						synchronized (threadLock) {threadLock.notifyAll();}
					}
					return null;
				} else if (r > core && timeout && System.currentTimeMillis() - prevStop >= idleTime) {// idle timeout
					if (U.compareAndSetInt(this, RUNNING, r, r-1)) {
						prevStop = System.currentTimeMillis();
						return null;
					}
				}

				try {
					task = fastPath.poll(idleTime, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					continue;
				}
			}

			if (task != null && task != IntMap.UNDEFINED)
				return (Runnable) task;

			timeout = true;
		}
	}

	private void newWorker() {
		int r = running;
		if (r < 0) throw new RejectedExecutionException(this+" was shutdown.");
		if (U.compareAndSetInt(this, RUNNING, r, r+1)) {
			prevStop = System.currentTimeMillis();

			var t = factory.get(this);
			synchronized (threadLock) {threads.add(t);}
			t.start();
		}
	}

	public void setRejectPolicy(RejectPolicy policy) { this.policy = policy; }
	public static void throwPolicy(TaskPool pool, Runnable task) {throw new RejectedExecutionException(pool+" is full ("+pool.tasks.size()+" tasks now), rejecting "+task);}
	public static void waitPolicy(TaskPool pool, Runnable task) {
		while (!pool.fastPath.tryTransfer(task) && !pool.tasks.offerLast(task)) {
			pool.noFull.awaitUninterruptibly();
			if (pool.running < 0) throw new RejectedExecutionException(pool+" was shutdown.");
		}
	}

	public void shutdown() {
		int r;
		do {
			r = running;
			if (r < 0) return;
		} while (!U.compareAndSetInt(this, RUNNING, r, -r-1));
		if (running < -1) synchronized (threadLock) {threadLock.notifyAll();}
	}
	public List<Runnable> shutdownNow() {
		shutdown();

		ArrayList<Runnable> tasks1;
		queueLock.lock();
		try {
			tasks1 = new ArrayList<>(tasks);
			tasks.clear();
			noFull.signalAll();
		} finally {
			queueLock.unlock();
		}

		while (true) {
			var o = fastPath.poll();
			if (!(o instanceof Runnable task)) break;
			tasks1.add(task);
		}

		while (fastPath.tryTransfer(IntMap.UNDEFINED));
		synchronized (threadLock) {
			for (Thread t : threads) t.interrupt();
		}

		for (Runnable runnable : tasks1) {
			ExecutorService.cancelIfCancellable(runnable);
		}
		return tasks1;
	}
	public boolean isShutdown() {return running < 0;}
	public boolean isTerminated() {return running == -1;}
	public void awaitTerminationInterruptibility() throws InterruptedException {
		if (!isShutdown()) throw new IllegalStateException("Not in shutdown state");

		synchronized (threadLock) {
			while (!isTerminated()) threadLock.wait();
		}
	}

	public int taskPending() {
		int r = running;
		return (r < 0 ? 0 : r-fastPath.getWaitingConsumerCount()) + tasks.size();
	}
	public int threadCount() {return running;}
	public int idleCount() {return fastPath.getWaitingConsumerCount();}
	public int busyCount() {return running - fastPath.getWaitingConsumerCount();}

	@Override
	public String toString() {return "TaskPool{"+factory+", threads=["+running+','+core+','+max+"], lock="+ queueLock +'}';}

	public class PoolThread extends FastLocalThread {
		public PoolThread(String name) {setName(name);setDaemon(true);}
		public static final Logger LOGGER = Logger.getLogger("ThreadPool");

		public TaskPool pool() {return TaskPool.this;}

		@Override
		public void run() {
			while (true) {
				Runnable task = pollTask();
				if (task == null) break;

				try {
					doRun(task);
				} catch (Throwable e) {
					exceptionHandler.uncaughtException(this, e);
				}
			}

			synchronized (threadLock) {threads.remove(this);}
		}

		private static void doRun(@Async.Execute Runnable task) { task.run(); }
	}

	public static MyThreadFactory namedPrefixFactory(String prefix) {
		final AtomicInteger ordinal = new AtomicInteger(1);
		return pool -> pool.new PoolThread(prefix+ordinal.getAndIncrement());
	}
}