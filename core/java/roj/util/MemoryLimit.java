package roj.util;

import roj.io.IOUtil;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Roj234
 * @since 2025/11/29 19:41
 */
public class MemoryLimit {
	private final AtomicLong memoryLimit = new AtomicLong();

	public MemoryLimit(long limit) {
		memoryLimit.set(limit);
	}

	public void acquire(long capacity) throws IOException {
		while (true) {
			long mem = memoryLimit.get();
			if (mem < capacity) {
				try {
					synchronized (this) {wait();}
				} catch (InterruptedException e) {
					throw IOUtil.rethrowAsIOException(e);
				}
			} else if (memoryLimit.compareAndSet(mem, mem-capacity)) {
				return;
			} else {
				Thread.onSpinWait();
			}
		}
	}

	public void release(long capacity) {
		memoryLimit.getAndAdd(capacity);
		synchronized (this) {
			notifyAll();
		}
	}

	public boolean tryAcquire(long capacity) {
		while (true) {
			long mem = memoryLimit.get();
			if (mem < capacity) {
				return false;
			} else if (memoryLimit.compareAndSet(mem, mem-capacity)) {
				return true;
			} else {
				Thread.onSpinWait();
			}
		}
	}
}
