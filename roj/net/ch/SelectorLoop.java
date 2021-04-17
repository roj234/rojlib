package roj.net.ch;

import roj.collect.SimpleList;
import roj.concurrent.FastLocalThread;
import roj.concurrent.Shutdownable;
import roj.util.Helpers;
import roj.util.HighResolutionTimer;

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ConcurrentModificationException;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author Roj233
 * @since 2022/1/24 11:38
 */
public class SelectorLoop implements Shutdownable {
	public static final BiConsumer<String, Throwable> PRINT_HANDLER = (reason, error) -> error.printStackTrace();
	static { HighResolutionTimer.activate(); }

	final class Poller extends FastLocalThread implements Consumer<SelectionKey> {
		private Selector selector;
		private long prevAlert;

		volatile boolean idle, wakeupLock;

		Poller() throws IOException {
			this.selector = MySelector.open();
			setName(prefix + " #" + index++);
			setDaemon(daemon);
		}

		final void refresh() {
			try {
				Selector old = selector;
				Selector sel = MySelector.open();

				for (SelectionKey oKey : new SimpleList<>(old.keys())) {
					Att att = (Att) oKey.attachment();
					try {
						att.s.register(sel, oKey.interestOps(), att);
					} catch (Exception e) {
						exception.accept("R_REGISTER", e);
						safeClose(att, oKey);
					}
				}

				selector = sel;

				old.close();
			} catch (Exception e) {
				exception.accept("R_OPEN", e);
			}
		}

		@Override
		public void run() {
			Selector sel = this.selector;
			SelectorLoop loop = SelectorLoop.this;
			MySelector selected = (MySelector) sel.selectedKeys();
			Set<SelectionKey> keys = MySelector.getForeacher(sel);
			long time = System.currentTimeMillis();
			int delayed = 0;

			mainLoop:
			while (!loop.wasShutdown() && sel.isOpen() && !Thread.interrupted()) {
				try {
					sel.select(1);
				} catch (IOException e) {
					exception.accept("S_SELECT", e);
					break;
				} catch (ClosedSelectorException e) {
					break;
				}

				if (!sel.isOpen()) break;

				while (wakeupLock) LockSupport.park();

				idle = true;
				if (keys.isEmpty()) {
					time = System.currentTimeMillis();
					while (true) {
						if (!sel.isOpen() || Thread.interrupted()) break mainLoop;
						if (!keys.isEmpty()) {
							time = System.currentTimeMillis();
							continue mainLoop;
						}
						if (System.currentTimeMillis() > time + loop.idleKill) {
							if (getRunningCount() > minThreads) break mainLoop;
						}

						LockSupport.parkNanos(loop.idleKill * 1_000_000L);
					}
				}

				idle = selected.isEmpty();
				for (int i = 0; i < selected.size(); i++) {
					SelectionKey key = selected.get(i);
					Att att = (Att) key.attachment();
					Selectable t = att.s;
					boolean closedOn;
					try {
						closedOn = t.isClosedOn(key);
					} catch (Exception e) {
						closedOn = uncaughtException(t, "T_IS_CLOSED_ON", e);
					}

					if (closedOn || !key.isValid()) {
						safeClose(att, key);
						continue;
					}

					try {
						t.selected(key.readyOps());
					} catch (Exception e) {
						if (uncaughtException(t, "T_SELECTED", e)) {
							safeClose(att, key);
						}
					}
				}

				if (!sel.isOpen()) break;

				int missedTime = (int) (System.currentTimeMillis() - time);
				if (missedTime >= 1) {
					delayed = 0;

					if (missedTime >= 50) {
						if (prevAlert - time > 10000) {
							System.err.println(Thread.currentThread().getName() + "@" + Thread.currentThread().getId() + "超过50ms未同步 请降低该线程的负载 / CT=" + missedTime);
							prevAlert = time;
						}
						missedTime = 1;
						time = System.currentTimeMillis();
					}

					int cycle = Math.min(missedTime, 10);
					time += cycle;

					while (cycle-- > 0) {
						//synchronized (sel.keys()) {
							try {
								keys.forEach(this);
							} catch (ConcurrentModificationException ignored) {
								// by source code, forEach only check modCount after iterate
								// since entry modification designed to by concurrent 'safe'
								// and we hold the lock
							}
						//}
					}
				} else {
					if (selected.isEmpty()) delayed++;

					if (delayed > 1000) {
						refresh();
						sel = selector;
						selected = (MySelector) sel.selectedKeys();
						keys = MySelector.getForeacher(sel);
						delayed = 0;
						System.err.println("rebuild selector");
						continue;
					}
				}

				selected.clear();
			}

			try {
				if (sel.isOpen()) sel.close();
			} catch (IOException e) {
				loop.exception.accept("S_CLOSE", e);
			}
			synchronized (loop.lock) {
				Poller[] t = loop.threads;
				for (int i = 0; i < t.length; i++) {
					if (t[i] == this) {
						int len = t.length - i - 1;
						if (len > 0) System.arraycopy(t, i + 1, t, i, len);
						t[t.length - 1] = null;
						break;
					}
				}
			}
		}

		@Override
		public void accept(SelectionKey key) {
			Att att = (Att) key.attachment();
			try {
				att.s.tick(1);
			} catch (Exception e) {
				if (uncaughtException(att.s, "T_TICK", e)) {
					safeClose(att, key);
				}
			}
		}

		private boolean uncaughtException(Selectable t, String stage, Exception e) {
			try {
				Boolean result = t.exceptionCaught(stage, e);
				if (result != null) return result;
			} catch (Exception e1) {
				e.addSuppressed(e1);
			}
			exception.accept(stage, e);
			return true;
		}

		private void safeClose(Att att, SelectionKey key) {
			key.cancel();

			try {
				att.s.close();
			} catch (Exception e) {
				uncaughtException(att.s, "T_CLOSE", e);
			}

			if (att.cb != null) {
				try {
					att.cb.accept(att.s);
				} catch (Exception ignored) {}
				att.cb = null;
			}
		}
	}

	private Shutdownable owner;
	private boolean shutdown;

	public final String prefix;
	private final int minThreads, maxThreads, idleKill, threshold;
	private boolean daemon;
	private BiConsumer<String, Throwable> exception = PRINT_HANDLER;

	protected final Object lock;
	protected Poller[] threads;

	int index;

	public SelectorLoop(Shutdownable owner, String prefix, int maxThreads) {
		this(owner, prefix, 1, 1, maxThreads, 30000, 32, true);
	}
	public SelectorLoop(Shutdownable owner, String prefix, int maxThreads, int idleKill, int threshold) {
		this(owner, prefix, 1, 1, maxThreads, idleKill, threshold, true);
	}
	public SelectorLoop(Shutdownable owner, String prefix, int minThreads, int maxThreads, int idleKill, int threshold) {
		this(owner, prefix, minThreads, minThreads, maxThreads, idleKill, threshold, true);
	}

	/**
	 * @param owner 关闭监听器
	 * @param prefix 线程名字前缀
	 * @param initThreads 初始线程
	 * @param minThreads 最小线程
	 * @param maxThreads 最大线程
	 * @param idleKill 选择器空置多久终止
	 * @param threshold 选择器中最少的都超过了这个值就再开一个线程
	 */
	public SelectorLoop(Shutdownable owner, String prefix, int initThreads, int minThreads, int maxThreads, int idleKill, int threshold, boolean daemon) {
		if (threshold < 0) throw new IllegalArgumentException("threshold < 0");
		if (minThreads < 0) throw new IllegalArgumentException("minThreads < 0");
		if (maxThreads < 1) throw new IllegalArgumentException("maxThreads < 1");
		if (idleKill < 1000) throw new IllegalArgumentException("idleKill < 1000");
		if (threshold > 1024) throw new IllegalArgumentException("threshold > 1024");

		this.prefix = prefix;
		this.minThreads = minThreads;
		this.maxThreads = maxThreads;
		this.idleKill = idleKill;
		this.threshold = threshold;
		this.lock = new Object();
		this.owner = owner;
		this.daemon = daemon;

		Thread[] t = this.threads = new Poller[Math.max(minThreads, 2)];
		for (int i = 0; i < initThreads; i++) {
			try {
				(t[i] = new Poller()).start();
			} catch (IOException e) {
				throw new IllegalStateException("Unable initialize thread", e);
			}
		}
	}

	public final void setExceptionHandler(BiConsumer<String, Throwable> exception) {
		this.exception = exception;
	}

	public final BiConsumer<String, Throwable> setExceptionHandler() {
		return exception;
	}

	public final void setOwner(Shutdownable s) {
		if (owner != null) throw new IllegalArgumentException("Already has a owner");
		owner = s;
	}

	public final void setDaemon(boolean daemon) {
		this.daemon = daemon;
	}

	public final int getStartedCount() {
		return index;
	}

	public final int getIdleCount() {
		int idle = 0;
		Poller[] t = this.threads;
		for (Poller thread : t) {
			if (thread == null) break;
			if (thread.idle) idle++;
		}
		return idle;
	}

	public void clear() {
		Poller[] t = threads;
		for (Poller thread : t) {
			if (thread == null) break;
			try {
				thread.selector.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public final int getRunningCount() {
		Poller[] t = this.threads;
		int i = 0;
		for (; i < t.length; i++) {
			if (t[i] == null) break;
		}
		return i;
	}

	public boolean wasShutdown() {
		if (shutdown) return true;
		if (owner != null && owner.wasShutdown()) {
			shutdown();
			return true;
		}
		return false;
	}

	public void shutdown() {
		synchronized (lock) {
			if (shutdown) return;
			shutdown = true;

			Poller[] t = this.threads;
			for (Poller thread : t) {
				if (thread == null) break;

				thread.interrupt();
				try {
					thread.selector.close();
				} catch (IOException ignored) {}
				thread.interrupt();
			}
		}
	}

	public final void register(Selectable t, Consumer<? extends Selectable> callback) throws IOException {
		register(t, callback, SelectionKey.OP_READ);
	}

	public void register(Selectable t, Consumer<? extends Selectable> callback, int ops) throws IOException {
		if (t == null) throw new NullPointerException("Selectable t");

		Att att = new Att();
		att.s = t;
		att.cb = Helpers.cast(callback);

		int i = 0;
		Poller lowest = null;

		Poller[] ts = this.threads;
		for (; i < ts.length; i++) {
			Poller st = ts[i];
			if (st == null) break;

			Selector s = st.selector;
			if (s.isOpen()) {
				if (lowest == null || s.keys().size() < lowest.selector.keys().size()) {
					lowest = st;
				}
			}
		}

		if (lowest != null) {
			if (lowest.selector.keys().size() <= threshold || i >= maxThreads) {
				try {
					lowest.wakeupLock = true;
					lowest.selector.wakeup();

					try {
						t.register(lowest.selector, ops, att);
						att.setThread(lowest);
					} finally {
						lowest.wakeupLock = false;
						LockSupport.unpark(lowest);
					}

					return;
				} catch (ClosedSelectorException ignored) {}
			}
		}

		Poller thread;
		synchronized (lock) {
			if (shutdown) return;

			if (i == ts.length) {
				ts = new Poller[Math.min(i + 10, maxThreads)];
				System.arraycopy(threads, 0, ts, 0, i);
				threads = ts;
			}
			thread = ts[i] = new Poller();
		}

		try {
			t.register(thread.selector, ops, att);
			att.setThread(thread);
		} finally {
			thread.start();
		}
	}

	static class Att {
		Selectable s;
		Consumer<Selectable> cb;

		void setThread(Poller t) {}
	}
}
