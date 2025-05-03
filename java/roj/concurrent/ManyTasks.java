package roj.concurrent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roj234-N
 * @since 2025/5/7 19:37
 */
public class ManyTasks {
	TaskExecutor executor;
	AtomicInteger counter;

	public ManyTasks(TaskExecutor executor) {
		this.executor = executor;
		this.counter = new AtomicInteger();
	}

	public void add(Task task) {
		counter.getAndIncrement();
		executor.submit(() -> {
			try {
				task.execute();
			} finally {
				if (counter.decrementAndGet() == 0) {
					notifyComplete();
				}
			}
		});
	}

	private void notifyComplete() {
		synchronized (counter) {
			notifyAll();
		}
	}

	public void waitAll() throws InterruptedException {
		while (counter.get() != 0) {
			synchronized (counter) {
				wait();
			}
		}
	}
}
