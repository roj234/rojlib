package roj.concurrent;

import org.jetbrains.annotations.Nullable;
import roj.optimizer.FastVarHandle;
import roj.reflect.Handles;
import roj.util.Helpers;
import roj.util.function.Flow;

import java.lang.invoke.VarHandle;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Roj234
 * @since 2025/09/02 23:03
 */
@FastVarHandle
public class TaskGroup implements Executor, Cancellable {
	@FastVarHandle
	private class MTask implements Runnable, Cancellable {
		volatile MTask next;
		Throwable throwable;

		private static final int INITIAL = 0, RUNNING = 1, CANCELLING = 2, CANCELLED = 3;
		private static final VarHandle STATE = Handles.lookup().findVarHandle(MTask.class, "state", int.class);
		private volatile int state;

		private Thread executor;

		private final Runnable callable;
		public MTask(Runnable c) { this.callable = c; }

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			if (STATE.compareAndSet(this, INITIAL, CANCELLED)) {
				taskCompleted();
				return true;
			}

			if (mayInterruptIfRunning && STATE.compareAndSet(this, RUNNING, CANCELLING)) {
				var thread = executor;
				if (thread != null) thread.interrupt();
			}

			return state <= CANCELLED;
		}

		@Override
		public boolean isCancelled() { return TaskGroup.this.isCancelled() || state == CANCELLED; }

		@Override
		public final void run() {
			if (!STATE.compareAndSet(this, INITIAL, RUNNING)) return;

			try {
				executor = Thread.currentThread();
				callable.run();
			} catch (Throwable e) {
				throwable = e;
				next = (MTask) FAILED.getAndSet(TaskGroup.this, this);
				if (failFast) TaskGroup.this.cancel();
			} finally {
				executor = null;
				taskCompleted();
			}
		}

		private void taskCompleted() {
			int val = (int) FINISHED.getAndAdd(TaskGroup.this, 1);
			if (val+1 >= getTotalTasks()) {
				synchronized (lock) {
					lock.notifyAll();
				}
			}
		}
	}

	private final Executor owner;
	private final Object lock = new Object();
	private final Set<MTask> helpRunner;

	static final VarHandle FAILED = Handles.lookup().findVarHandle(TaskGroup.class, "failedTasks", MTask.class);
	static final VarHandle FINISHED = Handles.lookup().findVarHandle(TaskGroup.class, "finishedTasks", int.class);
	static final VarHandle TOTAL = Handles.lookup().findVarHandle(TaskGroup.class, "totalTasks", int.class);

	private boolean failFast = true;
	private volatile MTask failedTasks;
	private volatile int finishedTasks, totalTasks;
	private CancellationException cancellationException;

	/**
	 * @param owner 执行的线程池
	 * @param helpRunTask 是否在 {@link #await(long, boolean)} 中顺带以当前线程执行任务 通常在当前线程就是线程池中的线程时开启
	 */
	public TaskGroup(Executor owner, boolean helpRunTask) {
		this.owner = owner;
		this.helpRunner = helpRunTask ? Collections.newSetFromMap(new ConcurrentHashMap<>()) : Collections.emptySet();
	}

	public void setFailFast(boolean failFast) {this.failFast = failFast;}
	public boolean isFailFast() {return failFast;}

	/**
	 * Submits a task for execution and monitors its progress.
	 * <p>
	 * If the monitor has been cancelled (via {@link #cancel(boolean)}), this method
	 * returns immediately without submitting the task.
	 *
	 * @param task the task to execute
	 * @throws NullPointerException if the task is null
	 */
	public void execute(Runnable task) {
		while (true) {
			int total = totalTasks;
			if (total < 0) return;
			if (TOTAL.compareAndSet(this, total, total+1)) break;
		}

		MTask task1 = new MTask(task);
		if (helpRunner != Collections.EMPTY_SET) {
			helpRunner.add(task1);
		}
		owner.execute(task1);
	}

	/**
	 * Waits indefinitely for all submitted tasks to complete.
	 * <p>
	 * If any task threw an exception, the first exception encountered is thrown upon completion,
	 * with other exceptions added as suppressed exceptions.
	 * <p>
	 * This method is not interruptible — if the current thread is interrupted,
	 * the interrupt status is set and waiting continues.
	 *
	 * @throws Throwable if any task threw an exception (with other exceptions suppressed)
	 */
	public void await() {await(0);}
	/**
	 * Waits for all submitted tasks to complete, with a timeout.
	 * <p>
	 * If the timeout elapses before all tasks complete, this method returns without throwing
	 * an exception. Any exceptions thrown by tasks are still available via {@link #clearExceptions()}.
	 * <p>
	 * This method is not interruptible — if the current thread is interrupted,
	 * the interrupt status is set and waiting continues.
	 *
	 * @param timeoutMs the maximum time to wait, in milliseconds (0 means wait indefinitely)
	 * @throws Throwable if any task threw an exception (with other exceptions suppressed)
	 */
	public void await(long timeoutMs) {await(timeoutMs, false);}
	/**
	 * Waits indefinitely for all submitted tasks to complete, with interruptible waiting.
	 * <p>
	 * If the current thread is interrupted while waiting, an {@link InterruptedException}
	 * is thrown. Any exceptions thrown by tasks are still available via {@link #clearExceptions()}.
	 *
	 * @throws InterruptedException if the current thread is interrupted while waiting
	 * @throws Throwable if any task threw an exception (with other exceptions suppressed)
	 */
	public void awaitInterruptibility() throws InterruptedException {await(0, true);}

	private void await(long timeoutMs, boolean interrupt) {
		long deadline = timeoutMs == 0 ? 0 : System.currentTimeMillis() + timeoutMs;
		while (finishedTasks < totalTasks) {
			long remain = deadline == 0 ? 0 : deadline - System.currentTimeMillis();
			if (remain < 0) break;

			if ((remain == 0 || remain > 100) && !helpRunner.isEmpty())
				Flow.of(helpRunner).filter(x -> x.state == MTask.INITIAL).findFirst().ifPresent(MTask::run);

			try {
				synchronized (lock) {
					if (finishedTasks < totalTasks)
						lock.wait(remain);
				}
			} catch (InterruptedException e) {
				if (interrupt) Helpers.athrow(e);
				else Thread.currentThread().interrupt();
			}
		}

		var cex = cancellationException;
		Throwable throwable = clearExceptions();
		if (throwable != null) {
			if (cex != null) cex.initCause(throwable);
			else Helpers.athrow(throwable);
		}
		if (cex != null) throw cex;
	}

	/**
	 * Clears and returns the first exception thrown by any task, with all other exceptions
	 * added as suppressed exceptions.
	 * <p>
	 * This method is atomic — subsequent calls will return {@code null} until new exceptions occur.
	 * It is typically called after waiting methods to retrieve aggregated exceptions.
	 *
	 * @return the first exception encountered, with other exceptions suppressed,
	 *         or {@code null} if no exceptions occurred
	 */
	@Nullable
	public Throwable clearExceptions() {
		var exceptionList = (MTask) FAILED.getAndSet(this, null);
		if (exceptionList == null) return null;

		Throwable ex = exceptionList.throwable;
		while (exceptionList.next != null) {
			exceptionList = exceptionList.next;
			ex.addSuppressed(exceptionList.throwable);
		}

		return ex;
	}

	/**
	 * Returns the underlying executor to which tasks are submitted.
	 *
	 * @return the executor used by this monitor
	 */
	public Executor owner() {return owner;}

	/**
	 * Returns the total number of tasks submitted to this monitor.
	 * <p>
	 * This count includes tasks that have completed, are currently running, or are yet to start.
	 *
	 * @return the total number of tasks submitted
	 */
	public int getTotalTasks() {return totalTasks &Integer.MAX_VALUE;}

	/**
	 * Attempts to cancel all tasks managed by this monitor.
	 * <p>
	 * This prevents new tasks from being submitted via {@link #execute(Runnable)} and
	 * marks the monitor as cancelled. Already running tasks may continue unless interrupted.
	 * <p>
	 * If {@code mayInterruptIfRunning} is {@code true}, this method attempts to interrupt
	 * all currently executing tasks. The interruption behavior depends on the task's
	 * implementation and the underlying executor.
	 *
	 * @param mayInterruptIfRunning {@code true} to interrupt running tasks
	 * @return {@code true} if the monitor was successfully cancelled
	 */
	@Override public boolean cancel(boolean mayInterruptIfRunning) {
		while (true) {
			int total = totalTasks;
			if (total < 0) return true;
			if (TOTAL.compareAndSet(this, total, total|Integer.MIN_VALUE)) {
				cancellationException = new CancellationException("(Async)");
				synchronized (lock) {lock.notifyAll();}
				return true;
			}
		}
	}

	/**
	 * Returns whether this monitor has been cancelled.
	 * <p>
	 * A cancelled monitor will reject new task submissions and may have interrupted
	 * some running tasks.
	 *
	 * @return {@code true} if the monitor has been cancelled
	 */
	@Override public boolean isCancelled() {return totalTasks < 0;}
}
