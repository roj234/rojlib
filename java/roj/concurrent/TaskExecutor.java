package roj.concurrent;

import roj.concurrent.task.ITask;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;

public class TaskExecutor extends FastLocalThread implements TaskHandler {
	ConcurrentLinkedQueue<ITask> tasks = new ConcurrentLinkedQueue<>();
	volatile boolean running = true;

	public TaskExecutor() {
		setName("TaskScheduler-"+hashCode());
		setDaemon(true);
	}

	@Override
	public void run() {
		while (running) {
			ITask task = tasks.peek();
			if (task == null) {
				synchronized (this) {notifyAll();}
				LockSupport.park();
				continue;
			}

			if (!task.isCancelled()) {
				try {
					task.execute();
				} catch (Throwable e) {
					if (!(e instanceof InterruptedException)) e.printStackTrace();
				}
			}
			tasks.poll();
		}
	}

	@Override
	public void pushTask(ITask task) {
		tasks.add(task);
		LockSupport.unpark(this);
	}

	@Override
	public void clearTasks() {
		ConcurrentLinkedQueue<ITask> queue = tasks;
		tasks = new ConcurrentLinkedQueue<>();
		for (ITask task : queue) task.cancel();
		queue.clear();

		LockSupport.unpark(this);
	}

	public void awaitFinish() throws InterruptedException {
		synchronized (this) {
			if (tasks.isEmpty()) return;
			wait();
		}
	}

	public void shutdown() {
		running = false;
		LockSupport.unpark(this);
	}
}