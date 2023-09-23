package roj.net.ch;

import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;
import roj.util.NamespaceKey;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Roj233
 * @since 2022/8/25 23:10
 */
public class CtxEmbedded extends MyChannel {
	public static final NamespaceKey EMBEDDED_CLOSE = NamespaceKey.of("embedded:close");
	private static final SocketAddress address = new SocketAddress() {
		@Override
		public String toString() {
			return "embedded";
		}
	};

	public static class Ticker extends Thread {
		private final SimpleList<CtxEmbedded> addPending = new SimpleList<>();
		private final MyHashSet<CtxEmbedded> ch = new MyHashSet<>();
		private volatile boolean shutdown, notify;

		@Override
		public void run() {
			long prevTick = System.currentTimeMillis();
			while (!shutdown) {
				if (ch.isEmpty() && !notify) LockSupport.park();
				synchronized (addPending) {
					ch.addAll(addPending);
					addPending.clear();
					notify = false;
				}

				long time = System.currentTimeMillis();
				int delta = (int) (time-prevTick);
				if (delta > 0) {
					for (Iterator<CtxEmbedded> itr = ch.iterator(); itr.hasNext(); ) {
						CtxEmbedded ctx = itr.next();

						if (ctx.state >= CLOSE_PENDING) {
							itr.remove();
							continue;
						}

						try {
							ctx.tick(delta);
						} catch (Throwable e) {
							try {
								if (ctx.exceptionCaught("TICK", e)) {
									e.printStackTrace();
								}
							} catch (Throwable e1) {
								e1.printStackTrace();
							}
						}
					}
				}

				LockSupport.parkNanos(1000_000L);
			}
		}

		final void tick(CtxEmbedded e) {
			synchronized (addPending) { addPending.add(e); }
			notify = true;
			LockSupport.unpark(this);
		}
	}
	private static Ticker defaultTicker;
	public static synchronized Ticker getDefaultTicker() {
		if (defaultTicker == null) {
			defaultTicker = new Ticker();
			defaultTicker.setDaemon(true);
			defaultTicker.setName("CtxEmbedded Ticker");
			defaultTicker.start();
		}
		return defaultTicker;
	}


	byte closeFlag;
	CtxEmbedded pair;
	Exception ex;

	public CtxEmbedded getPair() { return pair; }

	CtxEmbedded() {}

	public static CtxEmbedded createSingle() { return new CtxEmbedded(); }
	public static CtxEmbedded[] createPair() { return createPair(getDefaultTicker()); }
	public static CtxEmbedded[] createPair(Ticker sched) {
		CtxEmbedded left = new CtxEmbedded(), right = new CtxEmbedded();
		left.pair = right;
		right.pair = left;

		if (sched != null) {
			sched.tick(left);
			sched.tick(right);
		}

		return new CtxEmbedded[] {left, right};
	}

	// region simple overrides
	@Override
	public boolean isOpen() { return state < CLOSED && ((closeFlag & 1) == 0); }
	@Override
	public boolean isInputOpen() { return state < CLOSED && ((closeFlag & 2) == 0); }
	@Override
	public boolean isOutputOpen() { return state < CLOSED && ((closeFlag & 4) == 0); }

	private SocketAddress remote = address, local = address;

	public void setRemote(SocketAddress x) { remote = x; if (pair != null) pair.local = x; }
	public void setLocal(SocketAddress x) { local = x; if (pair != null) pair.remote = x; }

	@Override
	public SocketAddress localAddress() { return local; }
	@Override
	public SocketAddress remoteAddress() { return remote; }

	@Override
	public void register(Selector sel, int ops, Object att) throws IOException { throw new IOException("Channel is embedded"); }

	@Override
	public <T> MyChannel setOption(SocketOption<T> k, T v) throws IOException { return this; }
	@Override
	public <T> T getOption(SocketOption<T> k) throws IOException { throw new IOException("Channel is embedded"); }

	@Override
	protected boolean connect0(InetSocketAddress na) throws IOException { throw new IOException("Channel is embedded"); }
	@Override
	protected SocketAddress finishConnect0() throws IOException { throw new IOException("Channel is embedded"); }
	@Override
	protected void disconnect0() throws IOException { throw new IOException("Channel is embedded"); }

	@Override
	protected void read() throws IOException {}
	// endregion

	public void readActive() {
		byte f = flag;
		setFlagLock(f & ~READ_INACTIVE);
		if ((f & READ_INACTIVE) != 0) {
			if (pair != null) {
				try {
					pair.flush();
				} catch (IOException e) {
					pair.ex = e;
				}
			}
		}
	}
	public void readInactive() { setFlagLock(flag | READ_INACTIVE); }

	@Override
	public void tick(int elapsed) throws Exception {
		super.tick(elapsed);

		Exception e = ex;
		if (e != null) {
			ex = null;
			throw e;
		}
	}

	@Override
	protected void closeGracefully0() throws IOException {
		flag |= 4;
		if (pair != null) {
			pair.flag |= 2;
			pair.onInputClosed();

			if (pair.flag == 6) close();
		}

		if (flag == 6) close();
	}

	public void flush() throws IOException {
		if (state >= CLOSED) return;
		fireFlushing();
		if (pending.isEmpty()) return;

		lock.lock();
		try {
			do {
				Object buf = pending.peekFirst();
				if (buf == null) break;

				if (pair.readDisabled()) return;
				pair.fireChannelRead(buf);
				if (buf instanceof DynByteBuf && ((DynByteBuf) buf).isReadable()) return;

				pending.pollFirst();
			} while (true);

			if (pending.isEmpty()) {
				flag &= ~PAUSE_FOR_FLUSH;
				key.interestOps(SelectionKey.OP_READ);
				fireFlushed();
			}
		} finally {
			lock.unlock();
		}
	}
	protected void write(Object o) throws IOException {
		if (pair.readDisabled()) pending.ringAddLast(o);
		else pair.fireChannelRead(o);
	}

	@Override
	protected void closeHandler() throws IOException {
		super.closeHandler();

		closeFlag = 7;
		if (pair != null) pair.close();
	}
}