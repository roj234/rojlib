package roj.concurrent;

import org.jetbrains.annotations.Async;
import roj.compiler.api.Synchronizable;
import roj.reflect.Reflection;
import roj.reflect.Unaligned;
import roj.util.ArrayUtil;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.LockSupport;

import static roj.reflect.Unaligned.U;

@Synchronizable
public class TaskThread extends FastLocalThread implements ExecutorService {
	private ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
	private UncaughtExceptionHandler exceptionHandler = LOG_HANDLER;

	static final long STATE = Unaligned.fieldOffset(TaskThread.class, "state");
	//0 => running, 1 => terminating, 2 => stopped
	volatile int state = 0;

	public TaskThread() {this("RojLib - 未命名任务线程#"+Reflection.uniqueId());}
	public TaskThread(String name) {
		setName(name);
		setDaemon(true);
	}

	public void setExceptionHandler(UncaughtExceptionHandler handler) {exceptionHandler = handler;}

	@Override
	public void run() {
		while (true) {
			Runnable task = tasks.peek();
			if (task == null) {
				synchronized (this) {notifyAll();}
				LockSupport.park();
				if (state != 0) break;
				continue;
			}

			try {
				doRun(task);
			} catch (Throwable e) {
				exceptionHandler.uncaughtException(this, e);
			}

			if (tasks.poll() == null) break;
		}

		state = 2;
	}

	private static void doRun(@Async.Execute Runnable task) { task.run(); }

	@Override
	public void execute(@Async.Schedule Runnable task) {
		if (state != 0) throw new RejectedExecutionException("TaskExecutor was shutdown.");
		tasks.add(task);
		LockSupport.unpark(this);
	}
	@Override
	public void shutdown() {
		if (U.compareAndSetInt(this, STATE, 0, 1))
			LockSupport.unpark(this);
	}
	@Override
	public List<Runnable> shutdownNow() {
		var queue = tasks;
		tasks = new ConcurrentLinkedQueue<>();
		shutdown();
		List<Runnable> tasks = ArrayUtil.immutableCopyOf(queue);
		for (Runnable task : tasks) ExecutorService.cancelIfCancellable(task);
		return tasks;
	}
	@Override
	public boolean isShutdown() {return state != 0;}
	@Override
	public boolean isTerminated() {return state == 2;}

	public void awaitTerminationInterruptibility() throws InterruptedException {
		synchronized (this) {
			while (!tasks.isEmpty()) wait();
		}
	}
}