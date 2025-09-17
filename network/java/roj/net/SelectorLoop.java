package roj.net;

import roj.collect.ArrayList;
import roj.concurrent.FastLocalThread;
import roj.io.IOUtil;
import roj.util.Helpers;
import roj.util.JVM;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ConcurrentModificationException;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static roj.net.Net.LOGGER;

/**
 * @author Roj233
 * @since 2022/1/24 11:38
 */
public final class SelectorLoop implements Closeable {
	public static final BiConsumer<String, Throwable> PRINT_HANDLER = (reason, error) -> LOGGER.warn("在{}阶段发生了未处理的异常", error, reason);

	final class Poller extends FastLocalThread implements Consumer<SelectionKey> {
		private Selector selector;
		private long prevAlert;

		volatile boolean idle, wakeupLock;

		static { JVM.useAccurateTiming(); }
		Poller() throws IOException {
			this.selector = MySelector.open();
			setName(prefix+" #"+index++);
			setDaemon(daemon);
		}

		final void refresh() {
			var old = selector;
			Selector sel;
			try {
				selector = sel = MySelector.open();
			} catch (Exception e) {
				exception.accept("R_OPEN", e);
				return;
			}

			var keys = new ArrayList<>(old.keys());
			IOUtil.closeSilently(old);

			for (var key : keys) {
				var att = (Att) key.attachment();
				try {
					att.channel.register(sel, key.interestOps(), att);
				} catch (Exception e) {
					exception.accept("R_REGISTER", e);
					safeClose(att, key);
				}
			}

			selector = sel;
		}

		@Override
		public void run() {
			var sel = selector;
			var loop = SelectorLoop.this;
			var selected = (MySelector) sel.selectedKeys();
			var keys = MySelector.getIterable(sel);
			long time = System.currentTimeMillis();
			int delayed = 0;

			mainLoop:
			while (!loop.isClosed() && sel.isOpen() && !Thread.interrupted()) {
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
					Selectable t = att.channel;
					boolean closedOn;
					try {
						closedOn = t.isClosedOn(key);
					} catch (Throwable e) {
						closedOn = uncaughtException(t, "T_IS_CLOSED_ON", e);
					}

					if (closedOn || !key.isValid()) {
						safeClose(att, key);
						continue;
					}

					try {
						t.selected(key.readyOps());
					} catch (Throwable e) {
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
							LOGGER.warn("网络IO线程'{}'连续{}ms未同步 请降低该线程的负载", Thread.currentThread().getName(), missedTime);
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
					delayed += selected.isEmpty() ? 10 : 1;
					if (delayed > 10000) {
						int prevSize = sel.keys().size();
						refresh();
						sel = selector;
						selected = (MySelector) sel.selectedKeys();
						keys = MySelector.getIterable(sel);
						delayed = 0;
						LOGGER.warn("重建选择器, size={}/{}", prevSize, sel.keys().size());
						continue;
					}

					if (missedTime < 0) {
						time = System.currentTimeMillis();
						if (missedTime < -1) LOGGER.warn("时间倒流了{}ms", -missedTime);
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
				att.channel.tick(1);
			} catch (Throwable e) {
				if (uncaughtException(att.channel, "T_TICK", e)) {
					safeClose(att, key);
				}
			}
		}

		private boolean uncaughtException(Selectable t, String stage, Throwable e) {
			try {
				Boolean result = t.exceptionCaught(stage, e);
				if (result != null) return result;
			} catch (Throwable e1) {
				if (e != e1) e.addSuppressed(e1);
			}
			exception.accept(stage, e);
			return true;
		}

		private void safeClose(Att att, SelectionKey key) {
			key.cancel();

			try {
				att.channel.close();
				} catch (Throwable e) {
				uncaughtException(att.channel, "T_CLOSE", e);
			}

			if (att.callback != null) {
				try {
					att.callback.accept(att.channel);
				} catch (Throwable e) {
					uncaughtException(att.channel, "T_CALLBACK", e);
				}
				att.callback = null;
			}
		}
	}

	private boolean closed;

	final String prefix;
	private final int minThreads, maxThreads, idleKill, threshold;
	private boolean daemon;
	private BiConsumer<String, Throwable> exception = PRINT_HANDLER;

	final Object lock;
	Poller[] threads;

	int index;

	public SelectorLoop(String prefix, int maxThreads) {this(prefix, 1, 1, maxThreads, 30000, 32, true);}
	public SelectorLoop(String prefix, int maxThreads, int idleKill, int threshold) {this(prefix, 1, 1, maxThreads, idleKill, threshold, true);}
	public SelectorLoop(String prefix, int minThreads, int maxThreads, int idleKill, int threshold) {this(prefix, minThreads, minThreads, maxThreads, idleKill, threshold, true);}

	/**
	 * @param prefix 线程名字前缀
	 * @param initThreads 初始线程
	 * @param minThreads 最小线程
	 * @param maxThreads 最大线程
	 * @param idleKill 选择器空置多久终止
	 * @param threshold 选择器中最少的都超过了这个值就再开一个线程
	 */
	public SelectorLoop(String prefix, int initThreads, int minThreads, int maxThreads, int idleKill, int threshold, boolean daemon) {
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

	public final void setExceptionHandler(BiConsumer<String, Throwable> exception) {this.exception = exception;}
	public final BiConsumer<String, Throwable> setExceptionHandler() {return exception;}

	/**
	 * 只影响新创建的线程
	 */
	public final void setDaemon(boolean daemon) {this.daemon = daemon;}

	public final int getStartedCount() {return index;}
	public final int getIdleCount() {
		int idle = 0;
		var t = threads;
		for (var thread : t) {
			if (thread == null) break;
			if (thread.idle) idle++;
		}
		return idle;
	}
	public final int getRunningCount() {
		var t = threads;
		int i = 0;
		for (; i < t.length; i++) {
			if (t[i] == null) break;
		}
		return i;
	}

	public boolean isClosed() {return closed;}
	public void close() {
		synchronized (lock) {
			if (closed) return;
			closed = true;

			Poller[] t = threads;
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
		att.channel = t;
		att.callback = Helpers.cast(callback);

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
			if (closed) throw new IOException("SelectorLoop was shutdown.");

			if (i == ts.length) {
				ts = new Poller[Math.min(i + 10, maxThreads)];
				System.arraycopy(threads, 0, ts, 0, i);
				threads = ts;
			}
			thread = ts[i] = new Poller();
		}

		try {
			t.register(thread.selector, ops, att);
		} finally {
			thread.start();
		}
	}

	static final class Att {
		Selectable channel;
		Consumer<Selectable> callback;
	}
}