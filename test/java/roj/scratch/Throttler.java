package roj.scratch;

import roj.concurrent.Timer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roj234
 * @since 2025/10/30 01:00
 */
class Throttler {
	private final AtomicInteger updates = new AtomicInteger(0);
	private final int delay;
	private final Runnable task;

	public Throttler(int delay, Runnable task) {
		this.delay = delay;
		this.task = task;
	}

	public void update() {
		if (updates.getAndIncrement() == 0) delayedUpdate();
	}

	private void delayedUpdate() {
		Timer.getDefault().delay(() -> {
			task.run();

			if (updates.getAndSet(0) != 1) delayedUpdate();
		}, delay);
	}
}
