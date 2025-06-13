package roj.net;

import roj.collect.HashSet;
import roj.collect.ArrayList;
import roj.io.buf.BufferPool;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

/**
 * @author Roj233
 * @since 2022/8/25 23:10
 */
public class EmbeddedChannel extends MyChannel {
	public static final String EMBEDDED_CLOSE = "e_channel:close";
	private static final SocketAddress address = new SocketAddress() {
		@Override
		public String toString() {
			return "embedded";
		}
	};

	public static class Ticker extends Thread {
		private final ArrayList<EmbeddedChannel> addPending = new ArrayList<>();
		private final HashSet<EmbeddedChannel> ch = new HashSet<>();
		private volatile boolean shutdown, notify;

		@Override
		public void run() {
			long prevTick = System.currentTimeMillis();
			while (!shutdown) {
				//if (ch.isEmpty() && !notify) LockSupport.park();
				synchronized (addPending) {
					ch.addAll(addPending);
					addPending.clear();
					notify = false;
				}

				long time = System.currentTimeMillis();
				int delta = (int) (time-prevTick);
				if (delta > 0) {
					prevTick = time;
					for (Iterator<EmbeddedChannel> itr = ch.iterator(); itr.hasNext(); ) {
						EmbeddedChannel ctx = itr.next();

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

		final void tick(EmbeddedChannel e) {
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
	Function<Object, Boolean> writer;
	EmbeddedChannel pair;
	Exception ex;

	public EmbeddedChannel getPair() { return pair; }

	EmbeddedChannel() {}

	public static EmbeddedChannel createReadonly() { return new EmbeddedChannel(); }
	public static EmbeddedChannel createWritable(Function<Object, Boolean> writer) { return createWritable(writer, getDefaultTicker()); }
	public static EmbeddedChannel createWritable(Function<Object, Boolean> writer, Ticker sched) {
		EmbeddedChannel ch = new EmbeddedChannel();
		ch.writer = writer;
		if (sched != null) sched.tick(ch);
		return ch;
	}
	public static EmbeddedChannel[] createPair() { return createPair(getDefaultTicker()); }
	public static EmbeddedChannel[] createPair(Ticker sched) {
		EmbeddedChannel left = new EmbeddedChannel(), right = new EmbeddedChannel();
		left.pair = right;
		right.pair = left;

		if (sched != null) {
			sched.tick(left);
			sched.tick(right);
		}

		return new EmbeddedChannel[] {left, right};
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
	protected void bind0(InetSocketAddress na) throws IOException { throw new IOException("Channel is embedded"); }
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
		lock.lock();
		byte f = flag;
		flag = (byte) (f & ~READ_INACTIVE);
		lock.unlock();
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
	public void readInactive() {
		lock.lock();
		flag |= READ_INACTIVE;
		lock.unlock();
	}

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
			pair.onInputClosed(null);

			if (pair.flag == 6) close();
		}

		if (flag == 6) close();
	}

	public void flush() throws IOException {
		if (state >= CLOSED) return;
		if (pending.isEmpty()) return;

		lock.lock();
		try {
			var buf = (DynByteBuf) pending.peekFirst();
			if (buf == null) return;

			write0(buf);
			if (buf.isReadable()) return;

			assert pending.size() == 1 : "composite buffer violation";
			pending.clear();
			BufferPool.reserve(buf);

			flag &= ~(PAUSE_FOR_FLUSH|TIMED_FLUSH);
			fireFlushed();
		} finally {
			lock.unlock();
		}
	}
	protected void write(Object o) throws IOException {
		flush();

		var buf = (DynByteBuf) o;
		if (!buf.isReadable()) return;

		try {
			if (pending.isEmpty()) write0(buf = new ByteList().put(buf));

			if (buf.isReadable()) {
				DynByteBuf flusher;

				if (pending.isEmpty()) {
					fireFlushing();
					flusher = DynByteBuf.allocateDirect(buf.readableBytes(), 1048576);
					pending.offerLast(flusher);
				} else {
					flusher = (DynByteBuf) pending.getFirst();
				}

				if (flusher.unsafeWritableBytes() < buf.readableBytes()) flusher.compact();
				flusher.put(buf);
			} else {
				fireFlushed();
			}
		} finally {
			buf.rIndex = buf.wIndex();
		}
	}

	private void write0(DynByteBuf buf) throws IOException {
		if (writer != null) {
			if (writer.apply(buf)) {
			}
		} else {
			if (!pair.readDisabled() && pair.lock().tryLock()) {
				try {
					pair.fireChannelRead(buf);
				} finally {
					pair.lock.unlock();
				}
			}
		}
	}

	@Override
	protected void closeHandler() throws IOException {
		super.closeHandler();

		closeFlag = 7;
		if (pair != null) pair.close();
	}
}