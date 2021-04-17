package roj.concurrent;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * @author Roj233
 * @since 2022/3/14 13:14
 */
public abstract class DualBuffered<W, R> {
	final class Sync extends AbstractQueuedSynchronizer {
		private static final int WRITING = 4, DIRTY = 2, PAUSE = 1;
		private final int WriteUnlock;

		Sync(boolean instant) {
			WriteUnlock = instant ? DIRTY|PAUSE : DIRTY;
		}

		// write lock
		protected final boolean tryAcquire(int unused) {
			Thread current = Thread.currentThread();

			for(;;) {
				int c = getState();
				if ((c & PAUSE) != 0) return false;
				if ((c & WRITING) != 0) {
					if (getExclusiveOwnerThread() == current)
						throw new IllegalMonitorStateException("no-reentrant support");
					return false;
				}

				if (compareAndSetState(c, c|WRITING)) break;
			}

			setExclusiveOwnerThread(current);
			return true;
		}
		// write unlock
		protected final boolean tryRelease(int unused) {
			if (Thread.currentThread() != getExclusiveOwnerThread())
				throw new IllegalMonitorStateException();

			for(;;) {
				int c = getState();

				if ((c&WRITING) == 0) throw new IllegalMonitorStateException("未锁定");
				if (compareAndSetState(c, (c^WRITING) | WriteUnlock)) {
					if ((c&~(DIRTY|PAUSE)) == 0) tryInvokeFlush();
					break;
				}
			}

			setExclusiveOwnerThread(null);
			return true;
		}

		// read lock
		protected final int tryAcquireShared(int unused) {
			int limit = AQSHelper.INSTANCE.apparentlyFirstQueuedIsExclusive(this) ? 1 : 127;
			while (limit > 0) {
				int c = getState();
				if ((c & PAUSE) != 0) return -1;

				if (c+8 < 0) throw new IllegalStateException("计数器溢出");
				if (compareAndSetState(c, c+8)) return 1;

				limit--;
			}

			return -1;
		}

		// read unlock
		protected final boolean tryReleaseShared(int unused) {
			for (;;) {
				int c = getState();

				if (c < 8) throw new IllegalMonitorStateException("未锁定");
				if (compareAndSetState(c, c-8)) {
					if ((c&~(DIRTY|PAUSE)) == 8) tryInvokeFlush();
					return true;
				}
			}
		}

		private void tryInvokeFlush() {
			// STW
			for (;;) {
				int c = getState();
				if ((c & PAUSE) != 0) break;
				if (compareAndSetState(c, c|PAUSE)) break;
			}

			int c = getState();
			if ((c &~(DIRTY|PAUSE)) == 0) {
				try {
					move();
				} finally {
					compareAndSetState(c, c^PAUSE);
				}
			}
		}
	}

	protected W w;
	protected R r;
	private final Sync sync = new Sync(false);

	public DualBuffered() {}

	public DualBuffered(W w, R r) {
		this.w = w;
		this.r = r;
	}

	public final W forWrite() {
		sync.acquire(0);
		return w;
	}

	public final void writeFinish() {
		sync.release(0);
	}

	public final R forRead() {
		sync.acquireShared(0);
		return r;
	}

	public final void readFinish() {
		sync.releaseShared(0);
	}

	/**
	 * move W to R
	 */
	protected abstract void move();
}
