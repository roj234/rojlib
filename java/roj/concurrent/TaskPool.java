package roj.concurrent;

import org.jetbrains.annotations.Async;
import roj.collect.HashSet;
import roj.collect.IntMap;
import roj.collect.RingBuffer;
import roj.collect.ArrayList;
import roj.reflect.Unaligned;
import roj.text.logging.Logger;

import java.util.List;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

import static roj.reflect.Unaligned.U;

public class TaskPool implements TaskExecutor {
	@FunctionalInterface
	public interface MyThreadFactory {
		TaskPool.ExecutorImpl get(TaskPool pool);
	}

	@FunctionalInterface
	public interface RejectPolicy {
		void onReject(TaskPool pool, Task task);
	}

	final HashSet<ExecutorImpl> threads = new HashSet<>();
	private final MyThreadFactory factory;
	private RejectPolicy policy;
	private Thread.UncaughtExceptionHandler exceptionHandler;

	final int core, max, newThr, idleTime;
	private volatile int running;
	private volatile long prevStop;

	private static final long RUNNING_OFFSET = Unaligned.fieldOffset(TaskPool.class, "running");

	final ReentrantLock lock = new ReentrantLock();
	final Condition noFull = lock.newCondition();
	final RingBuffer<Task> tasks;
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

	@Override
	public void submit(@Async.Schedule Task task) {
		if (task.isCancelled()) return;

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

		lock.lock();
		try {
			if (len < 0) throw new RejectedExecutionException(this+" was shutdown.");
			if (!tasks.offerLast(task)) {
				if (policy == null) throwPolicy(this, task);
				else policy.onReject(this, task);
			}
		} finally {
			lock.unlock();
		}
	}

	private Task pollTask() {
		boolean timeout = false;

		for(;;) {
			if (!tasks.isEmpty()) {
				lock.lock();
				try {
					while (!tasks.isEmpty()) {
						Task task = tasks.removeFirst();
						if (!task.isCancelled()) return task;
					}
				} finally {
					noFull.signalAll();
					lock.unlock();
				}
			}

			Object task = fastPath.poll();
			if (task == null) {
				int r = running;
				if (r < 0) {// shutdown
					if (U.getAndAddInt(this, RUNNING_OFFSET, 1) == -2) {
						synchronized (threads) {threads.notifyAll();}
					}
					return null;
				} else if (r > core && timeout && System.currentTimeMillis() - prevStop >= idleTime) {// idle timeout
					if (U.compareAndSwapInt(this, RUNNING_OFFSET, r, r-1)) {
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

			if (task == null || task == IntMap.UNDEFINED) {
				timeout = true;
			} else {
				Task tt = (Task) task;
				if (!tt.isCancelled()) return tt;
				timeout = false;
			}
		}
	}

	private void newWorker() {
		int r = running;
		if (r < 0) throw new RejectedExecutionException(this+" was shutdown.");
		if (U.compareAndSwapInt(this, RUNNING_OFFSET, r, r+1)) {
			prevStop = System.currentTimeMillis();

			var t = factory.get(this);
			synchronized (threads) {threads.add(t);}
			t.start();
		}
	}

	public void setRejectPolicy(RejectPolicy policy) { this.policy = policy; }
	public static void throwPolicy(TaskPool pool, Task task) {throw new RejectedExecutionException(pool+" is full ("+pool.tasks.size()+" tasks now), rejecting "+task);}
	public static void waitPolicy(TaskPool pool, Task task) {
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
		} while (!U.compareAndSwapInt(this, RUNNING_OFFSET, r, -r-1));

		synchronized (this) {notifyAll();}
	}
	public List<Task> shutdownNow() {
		shutdown();

		ArrayList<Task> tasks1;
		lock.lock();
		try {
			tasks1 = new ArrayList<>(tasks);
			tasks.clear();
			noFull.signalAll();
			lock.lock();
			lock.unlock();
		} finally {
			lock.unlock();
		}

		while (true) {
			var task = fastPath.poll();
			if (!(task instanceof Task task1)) break;
			tasks1.add(task1);
		}

		while (fastPath.tryTransfer(IntMap.UNDEFINED));
		synchronized (threads) {
			for (Thread t : threads) t.interrupt();
		}
		return tasks1;
	}
	public boolean isShutdown() {return running < 0;}
	public boolean isTerminated() {return running == -1;}
	public void awaitTermination() throws InterruptedException {
		for(;;) {
			if (running < 0) {
				synchronized (threads) {
					while (running != -1) threads.wait();
				}
				return;
			}

			// 之前的写法有可能在多线程submit时误判（刚添加的任务还没来得及执行）
			// 现在应该不太可能了
			if (tasks.isEmpty() && fastPath.getWaitingConsumerCount() == running) break;
			LockSupport.parkNanos(1_000_000L);
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
	public String toString() {return "TaskPool{"+factory+", threads=["+running+','+core+','+max+"], lock="+lock+'}';}

	public class ExecutorImpl extends FastLocalThread {
		public ExecutorImpl(String name) {setName(name);setDaemon(true);}
		public static final Logger LOGGER = Logger.getLogger("ThreadPool");

		@Override
		public void run() {
			while (true) {
				Task task = pollTask();
				if (task == null) break;

				try {
					if (!task.isCancelled()) executeForDebug(task);
				} catch (Throwable e) {
					if (exceptionHandler != null) exceptionHandler.uncaughtException(this, e);
					else LOGGER.error("未捕获的异常", e);
				}
			}

			synchronized (threads) {threads.remove(this);}
		}

		private void executeForDebug(@Async.Execute Task task) throws Exception { task.execute(); }
	}

	public static MyThreadFactory namedPrefixFactory(String prefix) {
		final AtomicInteger ordinal = new AtomicInteger(1);
		return pool -> pool.new ExecutorImpl(prefix+ordinal.getAndIncrement());
	}
}