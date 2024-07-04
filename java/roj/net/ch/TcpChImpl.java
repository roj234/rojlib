package roj.net.ch;

import roj.io.NIOUtil;
import roj.io.buf.BufferPool;
import roj.reflect.DirectAccessor;
import roj.reflect.ReflectionUtils;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import static roj.util.ByteList.EMPTY;

/**
 * @author Roj233
 * @since 2022/5/18 0:00
 */
class TcpChImpl extends MyChannel {
	private static volatile H TcpUtil;
	private interface H {
		default boolean isInputOpen(SocketChannel sc) { return !isInputClosed(sc); }
		boolean isInputClosed(SocketChannel sc);
		default boolean isOutputOpen(SocketChannel sc) { return !isOutputClosed(sc); }
		boolean isOutputClosed(SocketChannel sc);
	}

	private SocketChannel sc;

	int buffer;

	TcpChImpl(int buffer) throws IOException {
		this(SocketChannel.open(), buffer);
		ch.configureBlocking(false);
		state = 0;
	}
	TcpChImpl(SocketChannel server, int buffer) {
		ch = sc = server;
		rb = EMPTY;
		this.buffer = buffer;
		state = CONNECTED;

		if (TcpUtil == null) {
			synchronized (TcpChImpl.class) {
				if (TcpUtil == null) {
					String[] fields = ReflectionUtils.JAVA_VERSION < 11 ? new String[] {"isInputOpen", "isOutputOpen"} : new String[] {"isInputClosed", "isOutputClosed"};
					TcpUtil = DirectAccessor.builder(H.class).access(server.getClass(), fields, fields, null).build();
				}
			}
		}
	}

	@Override
	public boolean isInputOpen() { return state < CLOSED && TcpUtil.isInputOpen(sc); }
	@Override
	public boolean isOutputOpen() { return state < CLOSED && TcpUtil.isOutputOpen(sc); }

	@Override
	public SocketAddress remoteAddress() {
		try {
			return sc.getRemoteAddress();
		} catch (IOException e) {
			return null;
		}
	}

	@Override
	public <T> MyChannel setOption(SocketOption<T> k, T v) throws IOException {
		if (k == ServerLaunch.TCP_RECEIVE_BUFFER) buffer = (Integer) v;
		else if (k == StandardSocketOptions.SO_REUSEPORT) NIOUtil.setReusePort(sc, (boolean) v);
		else sc.setOption(k, v);
		return this;
	}

	@Override
	protected void bind0(InetSocketAddress na) throws IOException { sc.bind(na); }
	@Override
	protected boolean connect0(InetSocketAddress na) throws IOException { return sc.connect(na); }
	@Override
	protected SocketAddress finishConnect0() throws IOException { return sc.finishConnect() ? sc.getRemoteAddress() : null; }
	@Override
	protected void closeGracefully0() throws IOException { if (sc.isOpen()) sc.shutdownOutput(); }
	@Override
	protected void disconnect0() throws IOException { sc.close(); ch = sc = SocketChannel.open(); rb.clear(); }

	public void flush() throws IOException {
		if (state >= CLOSED) return;
		fireFlushing();
		if (pending.isEmpty()) return;

		lock.lock();
		try {
			do {
				DynByteBuf buf = (DynByteBuf) pending.peekFirst();
				if (buf == null) break;

				write0(buf);

				if (buf.isReadable()) break;

				pending.pollFirst();
				BufferPool.reserve(buf);
			} while (true);

			if (pending.isEmpty()) {
				flag &= ~(PAUSE_FOR_FLUSH|TIMED_FLUSH);
				key.interestOps(SelectionKey.OP_READ);
				fireFlushed();
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	protected void read() throws IOException {
		if (state != OPENED || (flag&READ_INACTIVE) != 0) return;
		var buf = rb;
		if (buf == EMPTY) rb = buf = alloc().allocate(true, buffer, 0);
		while (true) {
			int w = buf.writableBytes();
			int r;
			ByteBuffer nioBuffer = syncNioRead(buf);
			try {
				r = sc.read(nioBuffer);
			} catch (IOException e) {
				if (TcpUtil.isOutputOpen(sc)) throw e;
				close();
				return;
			}
			buf.wIndex(nioBuffer.position());

			if (r < 0) {
				onInputClosed(buf);
				return;
			}

			if (r > 0) {
				fireChannelRead(buf);
				buf.compact();
				// reset to initial capacity if buffer is empty
				if (buf.capacity() > buffer && !buf.isReadable()) alloc().expand(buf, buffer-buf.capacity());
			}

			if (r < w || (flag&READ_INACTIVE) != 0 || state != OPENED) return;

			if (!buf.isWritable()) rb = buf = alloc().expand(buf, buf.capacity());
		}
	}

	protected void write(Object o) throws IOException {
		BufferPool bp = alloc();

		DynByteBuf buf = (DynByteBuf) o;
		if (!buf.isReadable()) return;
		if (!buf.isDirect()) buf = bp.allocate(true, buf.readableBytes(), 0).put(buf);

		try {
			write0(buf);

			if (buf.isReadable()) {
				if (pending.isEmpty()) key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
				else if (pending.size() > 5) pauseAndFlush();

				DynByteBuf put = bp.allocate(true, buf.readableBytes(), 0).put(buf);
				if (!pending.offerLast(put)) {
					BufferPool.reserve(put);
					throw new IOException("上层发送缓冲区过载");
				}
			} else {
				fireFlushed();
			}
		} finally {
			if (o != buf) BufferPool.reserve(buf);

			buf = (DynByteBuf) o;
			buf.rIndex = buf.wIndex();
		}
	}

	private void write0(DynByteBuf buf) throws IOException {
		ByteBuffer nioBuffer = syncNioWrite(buf);
		int w = sc.write(nioBuffer);
		buf.rIndex = nioBuffer.position();
	}
}