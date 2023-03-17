package roj.concurrent;

import org.jetbrains.annotations.Async;
import roj.collect.MyHashSet;
import roj.collect.RingBuffer;
import roj.concurrent.task.AsyncTask;
import roj.concurrent.task.ITask;
import roj.util.Helpers;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TaskPool implements TaskHandler {
	@FunctionalInterface
	public interface MyThreadFactory {
		TaskPool.ExecutorImpl get(TaskPool pool);
	}

	@FunctionalInterface
	public interface RejectPolicy {
		void onReject(TaskPool pool, ITask task);
	}

	private final Set<ExecutorImpl> threads = new MyHashSet<>();
	private final MyThreadFactory factory;
	private RejectPolicy policy;

	final int core, max, newThr, idleTime;
	private int running;
	private long prevStop;

	final ReentrantLock lock = new ReentrantLock(true);
	final Condition noFull = lock.newCondition(), noEmpty = lock.newCondition();
	final RingBuffer<ITask> tasks;

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
		if (newThreshold < 0) throw new IllegalArgumentException("newThreshold < 1");
		this.idleTime = stopTimeout;
		if (stopTimeout < 100) throw new IllegalArgumentException("stopTimeout < 100");
		this.factory = new PrefixFactory(namePrefix);

		tasks = new RingBuffer<>(64, 4096);
	}

	public TaskPool(int coreThreads, int maxThreads, int threshold) {
		this(coreThreads, maxThreads, threshold, 60000, "pool-" + Thread.currentThread().getName() + "-");
	}

	@Async.Schedule
	public AsyncTask<Void> pushRunnable(Runnable runnable) {
		AsyncTask<Void> task = AsyncTask.fromVoid(runnable);
		pushTask(task);
		return task;
	}

	@Override
	@Async.Schedule
	public void pushTask(ITask task) {
		if (task.isCancelled()) return;

		int len = running;
		if (len <= 0) {
			if (len < 0) {
				throw new RejectedExecutionException("TaskPool was shutdown.");
			}

			newWorker();
		} else {
			int size = tasks.size();

			if (len < core && size > 0) {
				newWorker();
			} else if (len < max && size >= newThr) {
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
				noEmpty.signal();
			}
		} finally {
			lock.unlock();
		}
	}

	private void newWorker() {
		lock.lock();
		try {
			ExecutorImpl pc = factory.get(this);
			pc.start();
			threads.add(pc);
			running++;
		} finally {
			lock.unlock();
		}
	}

	public void setRejectPolicy(RejectPolicy policy) {
		this.policy = policy;
	}

	public static void throwPolicy(TaskPool pool, ITask task) {
		throw new RejectedExecutionException("Too many tasks pending");
	}
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
		pool.noFull.awaitUninterruptibly();
		if (pool.running < 0) throw new RejectedExecutionException("TaskPool was shutdown.");

		pool.tasks.ringAddLast(task);
		pool.noEmpty.signal();
	}
	public static void newThreadPolicy(TaskPool pool, ITask task) {
		new ImmediateExecutor(task).start();
	}

	@Override
	public void clearTasks() {
		lock.lock();
		tasks.clear();
		noFull.signalAll();
		tasks.clear();
		lock.unlock();
	}

	boolean canExit(Thread t) {
		// closed
		if (running < 0) return true;

		if (running > core &&
			tasks.size() / running < newThr &&
			System.currentTimeMillis() - prevStop >= idleTime) {

			lock.lock();

			prevStop = System.currentTimeMillis();

			threads.remove(t);
			running--;

			lock.unlock();

			return true;
		}
		return false;
	}

	public void shutdown() {
		lock.lock();

		running = -1;
		tasks.clear();
		noEmpty.signalAll();
		noFull.signalAll();

		lock.unlock();

		synchronized (this) {
			notifyAll();
		}
	}

	public boolean working() {
		return running >= 0;
	}

	public Thread[] threads() {
		lock.lock();
		try {
			return threads.toArray(new Thread[threads.size()]);
		} finally {
			lock.unlock();
		}
	}

	public int taskPending() {
		return tasks.size();
	}

	public void waitUntilFinish() {
		synchronized (this) {
			try {
				if (tasks.isEmpty()) return;
				wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public boolean waitUntilFinish(long timeout)  {
		if (timeout <= 0) {
			waitUntilFinish();
		} else {
			synchronized (this) {
				if (tasks.isEmpty()) return true;
				try {
					wait(timeout);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

		return tasks.isEmpty();
	}

	@Override
	public String toString() {
		return "TaskPool{" + "thr=" + threads + ", range=[" + core + ',' + max + "], lock=" + lock + '}';
	}

	public static void yield() {
		Thread t = Thread.currentThread();
		if (t instanceof ExecutorImpl) {
			((ExecutorImpl) t).tryExecuteNext();
		}
	}

	public class ExecutorImpl extends FastLocalThread {
		int finished;

		protected ExecutorImpl() {
			setDaemon(true);
		}

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
			Lock lock = TaskPool.this.lock;

			out:
			while (working()) {
				ITask task;
				try {
					lock.lock();

					if (tasks.isEmpty()) {
						synchronized (TaskPool.this) {
							TaskPool.this.notifyAll();
						}
						do {
							if (!noEmpty.await(idleTime, TimeUnit.MILLISECONDS)) {
								if (canExit(this)) break out;
								continue out;
							}
						} while (tasks.isEmpty());
					}

					// 减少上下文切换的开销
					try {
						do {
							task = tasks.removeFirst();
						} while (task.isCancelled() && !tasks.isEmpty());
					} catch (Throwable e) {
						e.printStackTrace();
						continue;
					} finally {
						noFull.signal();
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
					continue;
				} finally {
					lock.unlock();
				}

				try {
					// double check after unlocked
					if (!task.isCancelled()) {
						task.execute();
						finished++;
						if (task.continueExecuting()) {
							pushTask(task);
						}
					}
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}

		void tryExecuteNext() {
			if (!working()) return;
			if (tasks.isEmpty()) return;

			ITask task;
			try {
				lock.lock();
				if (tasks.isEmpty()) return;

				try {
					do {
						task = tasks.removeFirst();
					} while (task.isCancelled() && !tasks.isEmpty());
				} catch (Throwable e) {
					e.printStackTrace();
					return;
				} finally {
					noFull.signal();
				}
			} finally {
				lock.unlock();
			}

			try {
				if (!task.isCancelled()) {
					task.execute();
					finished++;
					if (task.continueExecuting()) {
						pushTask(task);
					}
				}
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}
}