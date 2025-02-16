package roj.concurrent;

import roj.reflect.ReflectionUtils;
import roj.util.ArrayUtil;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.LockSupport;

import static roj.reflect.Unaligned.U;

public class TaskExecutor extends FastLocalThread implements TaskHandler {
	ConcurrentLinkedQueue<ITask> tasks = new ConcurrentLinkedQueue<>();

	static final long STATE_OFFSET = ReflectionUtils.fieldOffset(TaskExecutor.class, "state");
	//0 => running, 1 => terminating, 2 => stopped
	volatile int state = 0;

	public TaskExecutor() {
		setName("RojLib - 未命名任务线程#"+hashCode());
		setDaemon(true);
	}

	@Override
	public void run() {
		while (true) {
			ITask task = tasks.peek();
			if (task == null) {
				synchronized (this) {notifyAll();}
				LockSupport.park();
				if (state != 0) break;
				continue;
			}

			if (!task.isCancelled()) {
				try {
					task.execute();
				} catch (Throwable e) {
					if (!(e instanceof InterruptedException)) e.printStackTrace();
				}
			}

			if (tasks.poll() == null) break;
		}

		state = 2;
	}

	@Override
	public void submit(ITask task) {
		if (state != 0) throw new RejectedExecutionException("TaskExecutor was shutdown.");
		tasks.add(task);
		LockSupport.unpark(this);
	}
	@Override
	public void shutdown() {
		if (U.compareAndSwapInt(this, STATE_OFFSET, 0, 1))
			LockSupport.unpark(this);
	}
	@Override
	public List<ITask> shutdownNow() {
		var queue = tasks;
		tasks = new ConcurrentLinkedQueue<>();
		shutdown();
		return ArrayUtil.copyOf(queue);
	}
	@Override
	public boolean isShutdown() {return state != 0;}
	@Override
	public boolean isTerminated() {return state == 2;}

	public void awaitTermination() throws InterruptedException {
		synchronized (this) {
			while (!tasks.isEmpty()) wait();
		}
	}
}