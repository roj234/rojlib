package roj.net;

import roj.io.BufferPool;
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
	private static final int OUTPUT_CLOSED = 64;

	private SocketChannel sc;
	private int initialBufferCapacity, bufferRetainStrategy;

	TcpChImpl(int initialBufferCapacity) throws IOException {
		this(SocketChannel.open(), initialBufferCapacity, 0);
		ch.configureBlocking(false);
		state = 0;
	}
	TcpChImpl(SocketChannel server, int initialBufferCapacity, int bufferRetainStrategy) {
		ch = sc = server;
		rb = EMPTY;
		this.initialBufferCapacity = initialBufferCapacity;
		this.bufferRetainStrategy = bufferRetainStrategy;
		state = CONNECTED;
	}

	public void setInitialBufferCapacity(int initialBufferCapacity) {this.initialBufferCapacity = initialBufferCapacity;}
	public void setBufferRetainStrategy(int bufferRetainStrategy) {this.bufferRetainStrategy = bufferRetainStrategy;}

	@Override
	public boolean isOutputOpen() { return (flags&OUTPUT_CLOSED) == 0; }

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
		if (k == ServerLaunch.TCP_RECEIVE_BUFFER) initialBufferCapacity = (Integer) v;
		else if (k == StandardSocketOptions.SO_REUSEPORT) Net.setReusePort(sc, (boolean) v);
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
	protected void closeGracefully0() throws IOException {
		if (sc.isOpen()) sc.shutdownOutput();
		addFlag(OUTPUT_CLOSED);
	}
	@Override
	protected void disconnect0() throws IOException { sc.close(); ch = sc = SocketChannel.open(); rb.clear(); }

	@Override
	public void tick(int elapsed) throws Exception {
		super.tick(elapsed);

		if (bufferRetainStrategy > 0 && timeout >= bufferRetainStrategy && !rb.isReadable()) {
			lock.lock();
			try {
				if (!rb.isReadable()) {
					rb.release();
					rb = EMPTY;
				}
			} finally {
				lock.unlock();
			}
		}
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
			buf.release();

			flags &= ~(PAUSE_FOR_FLUSH|TIMED_FLUSH);
			key.interestOps(SelectionKey.OP_READ);
			fireFlushed();
		} finally {
			lock.unlock();
		}
	}

	@Override
	protected void read() throws IOException {
		if (state != OPENED || (flags&READ_INACTIVE) != 0) return;
		var buf = rb;
		if (buf == EMPTY) rb = buf = alloc().allocate(true, initialBufferCapacity, 0);
		while (true) {
			int w = buf.writableBytes();
			ByteBuffer nioBuffer = syncNioRead(buf);
			// IOException => exceptionCaught
			int r = sc.read(nioBuffer);
			buf.wIndex(nioBuffer.position());

			if (r < 0) {
				onInputClosed(buf);
				return;
			}

			if (r > 0) {
				fireChannelRead(buf);
				buf.compact();
				// reset to initial capacity if buffer is empty
				if (buf.capacity() > initialBufferCapacity && !buf.isReadable()) alloc().expand(buf, initialBufferCapacity -buf.capacity());
			}

			if (r < w || (flags&READ_INACTIVE) != 0 || state != OPENED) {
				if (!buf.isReadable() && bufferRetainStrategy == 0) {
					buf.release();
					rb = EMPTY;
				}
				return;
			}

			if (!buf.isWritable()) rb = buf = alloc().expand(buf, buf.capacity());
		}
	}

	protected void write(Object o) throws IOException {
		if (o instanceof SendfilePkt req) {
			if (!pending.isEmpty()) flush();
			req.written = pending.isEmpty() ? req.channel.transferTo(req.offset, req.length, sc) : -1;
			return;
		}

		var buf = (DynByteBuf) o;
		if (!buf.isReadable()) return;

		BufferPool bp = alloc();
		try {
			if (pending.isEmpty()) {
				if (!buf.isDirect()) buf = bp.allocate(true, buf.readableBytes(), 0).put(buf);
				write0(buf);
			}

			if (buf.isReadable()) {
				DynByteBuf flusher;

				// difference with EmbeddedChannel: #selected will automatically invoke flush()
				if (pending.isEmpty()) {
					fireFlushing();
					key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
					flusher = DynByteBuf.allocateDirect(buf.readableBytes(), 1048576);
					pending.offerLast(flusher);
				} else {
					flusher = (DynByteBuf) pending.getFirst();
				}

				if (flusher.unsafeWritableBytes() < buf.readableBytes()) flusher.compact();
				flusher.put(buf);
				if (flusher.readableBytes() > rb.capacity()) pauseAndFlush();
			} else {
				fireFlushed();
			}
		} finally {
			if (o != buf) buf.release();

			buf = (DynByteBuf) o;
			buf.rIndex = buf.wIndex();
		}
	}

	private void write0(DynByteBuf buf) throws IOException {
		var nioBuffer = syncNioWrite(buf);
		sc.write(nioBuffer);
		buf.rIndex = nioBuffer.position();
	}

	@Override public boolean canSendfile() {return true;}
}