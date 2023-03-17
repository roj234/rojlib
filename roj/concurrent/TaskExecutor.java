package roj.concurrent;

import roj.concurrent.task.AsyncTask;
import roj.concurrent.task.ITask;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.LockSupport;

public class TaskExecutor extends FastLocalThread implements TaskHandler, Executor {
	ConcurrentLinkedQueue<ITask> tasks = new ConcurrentLinkedQueue<>();
	volatile boolean running = true;

	public TaskExecutor() {
		setName("TaskScheduler-" + hashCode());
		setDaemon(true);
	}

	@Override
	public void run() {
		while (running) {
			ITask task;
			do {
				task = tasks.peek();
				if (task == null) {
					synchronized (this) {
						notifyAll();
					}

					do {
						LockSupport.park();
						if (!running) return;
					} while (tasks.isEmpty());
				} else if (task.isCancelled()) {
					tasks.poll();
				} else {
					break;
				}
			} while (true);

			try {
				task.execute();
			} catch (Throwable e) {
				if (!(e instanceof InterruptedException)) e.printStackTrace();
			}
			tasks.poll();
			try {
				if (task.continueExecuting()) {
					tasks.add(task);
				}
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void pushTask(ITask task) {
		tasks.add(task);
		LockSupport.unpark(this);
	}

	public boolean removeTask(ITask task) {
		return tasks.remove(task);
	}

	public int getTaskAmount() {
		return tasks.size();
	}

	@Override
	public void clearTasks() {
		ConcurrentLinkedQueue<ITask> queue = tasks;
		tasks = new ConcurrentLinkedQueue<>();
		for (ITask task : queue) task.cancel(true);
		queue.clear();

		LockSupport.unpark(this);
	}

	@Override
	public String toString() {
		return "TE{" + "task=" + tasks + '}';
	}

	@Override
	public void execute(Runnable command) {
		pushTask(AsyncTask.fromVoid(command));
	}

	public void waitFor() throws InterruptedException {
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
