package roj.net.ch;

import roj.collect.MyHashMap;
import roj.collect.RingBuffer;
import roj.collect.SimpleList;
import roj.concurrent.TaskPool;
import roj.io.buf.BufferPool;
import roj.util.*;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Roj233
 * @since 2022/5/17 12:47
 */
public abstract class MyChannel implements Selectable {
	public static final NamespaceKey IN_EOF = new NamespaceKey("input_eof");

	ChannelCtx pipelineHead, pipelineTail;

	private Map<TypedName<?>, Object> attachments = Collections.emptyMap();

	SelectionKey key = DummySelectionKey.INSTANCE;
	AbstractSelectableChannel ch;
	private NetworkChannel nc() { return (NetworkChannel) ch; }
	int timeout;

	protected DynByteBuf rb;
	BufferPool pool;

	public static final int INITIAL = 0, CONNECTED = 1, CONNECT_PENDING = 2, OPENED = 3, CLOSE_PENDING = 4, CLOSED = 5;
	protected volatile byte state;

	protected final RingBuffer<Object> pending = new RingBuffer<>(0, 100);

	protected static final int PAUSE_FOR_FLUSH = 1, REINVOKE_READ = 2, READ_INACTIVE = 4;
	protected byte flag;

	protected final ReentrantLock lock = new ReentrantLock();

	public static MyChannel openTCP() throws IOException {return openTCP(2048);}
	public static MyChannel openTCP(int buffer) throws IOException {return new TcpChImpl(buffer);}
	public static MyChannel openUDP() throws IOException {return new UdpChImpl(1536);}
	public static MyChannel openUDP(int buffer) throws IOException {return new UdpChImpl(buffer);}

	// protected only used for UDPoverTCP
	protected MyChannel() {}

	// region Pipeline
	public final ChannelCtx handler(String name) {
		ChannelCtx pipe = pipelineHead;
		while (pipe != null) {
			if (pipe.name.equals(name)) return pipe;
			pipe = pipe.next;
		}
		return null;
	}
	public final MyChannel addLast(String name, ChannelHandler pipe) {
		ChannelCtx cp = handler(name);
		if (cp != null) throw new IllegalArgumentException("Duplicate name " + name);
		cp = new ChannelCtx(this, name);
		cp.handler = pipe;

		lock.lock();

		cp.prev = pipelineTail;
		if (pipelineTail == null) {
			pipelineHead = cp;
		} else {
			pipelineTail.next = cp;
		}
		pipelineTail = cp;

		try {
			pipe.handlerAdded(cp);
		} finally {
			lock.unlock();
		}

		return this;
	}
	public final MyChannel addFirst(String name, ChannelHandler pipe) {
		ChannelCtx cp = handler(name);
		if (cp != null) throw new IllegalArgumentException("Duplicate name " + name);
		cp = new ChannelCtx(this, name);
		cp.handler = pipe;

		lock.lock();

		cp.next = pipelineHead;
		if (pipelineHead == null) {
			pipelineTail = cp;
		} else {
			pipelineHead.prev = cp;
		}
		pipelineHead = cp;

		try {
			pipe.handlerAdded(cp);
		} finally {
			lock.unlock();
		}
		return this;
	}
	public final MyChannel addAfter(String from, String name, ChannelHandler pipe) {
		ChannelCtx fr = handler(from);
		if (fr == null) throw new NullPointerException("[from] not found: " + from);
		return addAfter(fr,name,pipe);
	}
	public final MyChannel addAfter(ChannelCtx from, String name, ChannelHandler pipe) {
		ChannelCtx cp = handler(name);
		if (cp != null) throw new IllegalArgumentException("Duplicate name " + name);
		cp = new ChannelCtx(this, name);
		cp.handler = pipe;

		if (from == null) throw new NullPointerException("from");

		lock.lock();

		cp.next = from.next;
		cp.prev = from;
		if (from.next != null) from.next.prev = cp;
		else pipelineTail = cp;
		from.next = cp;

		try {
			pipe.handlerAdded(cp);
		} finally {
			lock.unlock();
		}
		return this;
	}
	public final MyChannel addBefore(String from, String name, ChannelHandler pipe) {
		ChannelCtx fr = handler(from);
		if (fr == null) throw new NullPointerException("[from] not found: " + from);
		return addBefore(fr, name, pipe);
	}
	public final MyChannel addBefore(ChannelCtx from, String name, ChannelHandler pipe) {
		ChannelCtx cp = handler(name);
		if (cp != null) throw new IllegalArgumentException("[name] exists: " + name);
		cp = new ChannelCtx(this, name);
		cp.handler = pipe;

		if (from == null) throw new NullPointerException("from");

		lock.lock();

		cp.prev = from.prev;
		cp.next = from;
		if (from.prev != null) from.prev.next = cp;
		else pipelineHead = cp;
		from.prev = cp;

		try {
			pipe.handlerAdded(cp);
		} finally {
			lock.unlock();
		}
		return this;
	}
	public final void replace(String name, ChannelHandler h) {
		ChannelCtx cp = handler(name);
		if (cp == null) throw new IllegalArgumentException("Not found handler " + name);
		cp.handler.handlerRemoved(cp);
		cp.handler = h;
		h.handlerAdded(cp);
	}
	public final ChannelHandler remove(String name) {
		return remove(handler(name));
	}
	public final void remove(ChannelHandler h) {
		ChannelCtx pipe = pipelineHead;
		while (pipe != null) {
			if (pipe.handler == h) {
				remove(pipe);
				return;
			}
			pipe = pipe.next;
		}
	}
	public final ChannelHandler remove(ChannelCtx cp) {
		if (cp == null) return null;

		lock.lock();

		if (cp.next != null) cp.next.prev = cp.prev;
		else pipelineTail = cp.prev;
		if (cp.prev != null) cp.prev.next = cp.next;
		else pipelineHead = cp.next;

		lock.unlock();

		cp.handler.handlerRemoved(cp);
		return cp.handler;
	}
	public final List<ChannelCtx> removeAll() {
		List<ChannelCtx> list = handlers();
		pipelineHead = pipelineTail = null;
		for (int i = 0; i < list.size(); i++) {
			list.get(i).handler.handlerRemoved(list.get(i));
		}
		return list;
	}
	public final List<ChannelCtx> handlers() {
		List<ChannelCtx> list = new SimpleList<>();
		ChannelCtx pipe = pipelineHead;
		while (pipe != null) {
			list.add(pipe);
			pipe = pipe.next;
		}
		return list;
	}
	public void movePipeFrom(MyChannel ch) {
		ChannelCtx pipe = ch.pipelineHead;
		while (pipe != null) {
			pipe.handler.handlerRemoved(pipe);
			pipe.handler.handlerAdded(pipe);

			pipe.root = this;

			pipe = pipe.next;
		}

		this.pipelineHead = ch.pipelineHead;
		this.pipelineTail = ch.pipelineTail;

		ch.pipelineHead = ch.pipelineTail = null;
	}
	// endregion

	public final int getState() {
		return state;
	}

	public boolean isOpen() {
		return state < CLOSED && ch.isOpen();
	}
	public boolean isInputOpen() { return isOpen(); }
	public boolean isOutputOpen() { return isOpen(); }

	public abstract SocketAddress remoteAddress();
	public SocketAddress localAddress() {
		try {
			return nc().getLocalAddress();
		} catch (IOException e) {
			return null;
		}
	}

	public <T> MyChannel setOption(SocketOption<T> k, T v) throws IOException {
		nc().setOption(k, v);
		return this;
	}
	public <T> T getOption(SocketOption<T> k) throws IOException {
		return nc().getOption(k);
	}

	@SuppressWarnings("unchecked")
	public final <T> T attachment(TypedName<T> key) {
		return (T) attachments.get(key);
	}
	@SuppressWarnings("unchecked")
	public synchronized final <T> T attachment(TypedName<T> key, T val) {
		if (attachments.isEmpty()) attachments = new MyHashMap<>(4);
		return (T) (val == null ? attachments.remove(key) : attachments.put(key, val));
	}

	// region State
	final void setFlagLock(int newFlag) {
		lock.lock();
		flag = (byte) newFlag;
		lock.unlock();
	}

	public void readActive() {
		int ops = key.interestOps();
		setFlagLock(flag & ~READ_INACTIVE);
		if ((ops & SelectionKey.OP_READ) == 0) {
			key.interestOps(ops | SelectionKey.OP_READ);

			DynByteBuf rb = this.rb;
			if (rb != null && rb.isReadable()) {
				invokeReadLater();
			}
		}
	}

	public void readInactive() {
		int ops = key.interestOps();
		setFlagLock(flag | READ_INACTIVE);
		if ((ops & SelectionKey.OP_READ) != 0) {
			key.interestOps(ops & ~SelectionKey.OP_READ);
		}
	}

	public void pauseAndFlush() {
		if (!pending.isEmpty()) {
			setFlagLock(flag | PAUSE_FOR_FLUSH);
			key.interestOps(SelectionKey.OP_WRITE);
		}
	}

	public final boolean isPendingSend() {
		return !pending.isEmpty();
	}
	// endregion

	public void closeGracefully() throws IOException {
		close();
	}

	public BufferPool alloc() {
		if (pool == null) {
			lock.lock();
			if (pool == null) pool = BufferPool.localPool();
			lock.unlock();
		}
		return pool;
	}
	public MyChannel alloc(BufferPool pool) {
		this.pool = pool;
		return this;
	}

	public final void postEvent(Event event) throws IOException {
		if (event == null) throw new NullPointerException("event");
		lock.lock();
		try {
			if (event._reverse()) {
				ChannelCtx pipe = pipelineHead;
				while (pipe != null) {
					pipe.handler.onEvent(pipe, event);
					if (event._stop()) break;
					pipe = pipe.next;
				}
			} else {
				ChannelCtx pipe = pipelineTail;
				while (pipe != null) {
					pipe.handler.onEvent(pipe, event);
					if (event._stop()) break;
					pipe = pipe.prev;
				}
			}
		} finally {
			lock.unlock();
		}
	}

	public void invokeReadLater() {
		setFlagLock(flag | REINVOKE_READ);
	}

	private ChannelCtx head() {
		if (pipelineHead == null) throw new IllegalStateException("pipeline is empty");
		return pipelineHead;
	}

	public final void fireChannelRead(Object data) throws IOException {
		lock.lock();
		try {
			ChannelCtx head = head();
			head.handler.channelRead(head, data);
		} finally {
			lock.unlock();
		}
	}
	public final void fireChannelWrite(Object data) throws IOException {
		lock.lock();
		try {
			ChannelCtx tail = pipelineTail;
			if (tail == null) write(data);
			else tail.handler.channelWrite(tail, data);
		} finally {
			lock.unlock();
		}
	}

	public final boolean connect(SocketAddress address) throws IOException {
		return connect(address, -1);
	}
	public final boolean connect(SocketAddress address, int timeout) throws IOException {
		if (!(address instanceof InetSocketAddress)) throw new UnsupportedOperationException("Not InetSocketAddress");
		InetSocketAddress na = (InetSocketAddress) address;

		lock.lock();
		try {
			if (state != INITIAL) {
				if (state > OPENED) throw new ClosedChannelException();
				throw new IOException("Must INITIAL not " + state);
			}

			this.timeout = timeout > 0 ? timeout : 0;
			if (key.isValid())
				key.interestOps(key.interestOps()|SelectionKey.OP_CONNECT);
			state = CONNECT_PENDING;
			boolean done = connect0(na);
			if (done) {
				state = CONNECTED;
				open();
			}
			return done;
		} finally {
			lock.unlock();
		}
	}
	public final boolean finishConnect() throws IOException {
		if (state != CONNECT_PENDING) throw new NoConnectionPendingException();

		lock.lock();
		try {
			if (state != CONNECT_PENDING) throw new NoConnectionPendingException();

			SocketAddress finished = finishConnect0();
			if (finished != null) {
				state = CONNECTED;
				open();
				return true;
			}
			return false;
		} finally {
			lock.unlock();
		}
	}
	public int getConnectTimeoutRemain() { return timeout; }

	public final void open() throws IOException {
		lock.lock();
		try {
			if (state == CONNECT_PENDING) throw new NotYetConnectedException();
			if (state == OPENED) {
				new Throwable("channel already open").printStackTrace();
				return;
			}
			if (state > OPENED) throw new ClosedChannelException();
			state = OPENED;

			ChannelCtx head = head();
			head.handler.channelOpened(head);
		} finally {
			lock.unlock();
		}
	}
	public final void disconnect() throws IOException {
		if (state == INITIAL) return;

		lock.lock();
		try {
			if (state == INITIAL) return;
			if (state > OPENED) throw new ClosedChannelException();

			key.cancel();
			key = DummySelectionKey.INSTANCE;

			disconnect0();
			ch.configureBlocking(false);

			state = INITIAL;
		} finally {
			lock.unlock();
		}
	}

	// region Selectable
	@Override
	public void tick(int elapsed) throws IOException {
		if (state >= CLOSED) return;
		if (state == CONNECT_PENDING && timeout != 0 && (timeout -= elapsed) < 0) {
			if (finishConnect()) return;
			close();
			throw new ConnectException("Connect timeout");
		}

		lock.lock();
		try {
			if ((flag & REINVOKE_READ) != 0) {
				flag ^= REINVOKE_READ;
				fireChannelRead(rb);
			}

			ChannelCtx pipe = pipelineHead;
			while (pipe != null) {
				pipe.handler.channelTick(pipe);
				if (state >= CLOSED) break;
				pipe = pipe.next;
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	public final boolean isClosedOn(SelectionKey key) {
		return key != this.key | !key.isValid();
	}

	@Override
	public final void close() throws IOException {
		if (state >= CLOSE_PENDING) return;

		lock.lock();
		try {
			if (state >= CLOSE_PENDING) return;
			state = CLOSE_PENDING;

			try {
				closeHandler();
			} finally {
				state = CLOSED;

				key.cancel();
				if (ch != null) ch.close();

				if (rb != null && rb.capacity() > 0) {
					alloc().reserve(rb);
					rb = null;
				}
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	public final void selected(int readyOps) throws Exception {
		if (state >= CLOSED) {
			key.cancel();
			return;
		}

		if (state == CONNECT_PENDING) {
			key.interestOps((key.interestOps() & ~SelectionKey.OP_CONNECT) | SelectionKey.OP_READ);
			if (!finishConnect()) return;
		}

		if (!pending.isEmpty() && (readyOps & SelectionKey.OP_WRITE) != 0) flush();
		if ((flag & PAUSE_FOR_FLUSH) != 0 || (readyOps & SelectionKey.OP_READ) == 0) return;

		if (!lock.tryLock()) return;
		try {
			read();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void register(Selector sel, int ops, Object att) throws IOException {
		lock.lock();
		try {
			if (state == INITIAL) throw new IllegalStateException("Should call connect() before register");
			else if (state == CONNECT_PENDING) ops |= SelectionKey.OP_CONNECT;
			if ((flag & READ_INACTIVE) != 0) ops &= ~SelectionKey.OP_READ;

			if (ch.keyFor(sel) != key) key.cancel();
			key = ch.register(sel, ops, att);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public final Boolean exceptionCaught(String stage, Throwable ex) throws Exception {
		lock.lock();
		try {
			ChannelCtx head = head();
			head.handler.exceptionCaught(head, ex);
			// 若关闭(未处理异常/主动关闭)，触发SelectorLoop后续过程
			// 否则，认为应用程序处理了该异常
			return state >= CLOSE_PENDING;
		} finally {
			lock.unlock();
		}
	}
	// endregion

	// abstract
	protected abstract boolean connect0(InetSocketAddress na) throws IOException;
	protected abstract SocketAddress finishConnect0() throws IOException;
	protected abstract void disconnect0() throws IOException;

	public abstract void flush() throws IOException;
	protected abstract void read() throws IOException;
	protected abstract void write(Object data) throws IOException;

	// callback

	final void onInputClosed() throws IOException {
		Event inputEndEvent = new Event(IN_EOF);
		postEvent(inputEndEvent);
		if (inputEndEvent.getResult() == Event.RESULT_DEFAULT) {
			close();
		}
	}

	protected void closeHandler() throws IOException {
		Throwable ee = null;
		ChannelCtx pipe = pipelineTail;
		while (pipe != null) {
			try {
				pipe.handler.channelClosed(pipe);
			} catch (Throwable e) {
				if (ee == null) ee = e;
				else ee.addSuppressed(e);
			}
			pipe = pipe.prev;
		}
		if (ee != null) Helpers.athrow(ee);
	}

	protected final void fireWriteDone() throws IOException {
		ChannelCtx pipe = pipelineHead;
		while (pipe != null) {
			pipe.handler.channelFlushed(pipe);
			pipe = pipe.next;
		}
	}

	private ByteBuffer nioBuf;
	final ByteBuffer syncNioWrite(DynByteBuf buf) {
		if (nioBuf == null) return nioBuf = buf.nioBuffer();
		else {
			NativeMemory.setBufferCapacityAndAddress(nioBuf, buf.address(), buf.capacity());
			nioBuf.limit(buf.wIndex()).position(buf.rIndex);
		}
		return nioBuf;
	}
	final ByteBuffer syncNioRead(DynByteBuf buf) {
		if (nioBuf == null) nioBuf = buf.nioBuffer();
		else NativeMemory.setBufferCapacityAndAddress(nioBuf, buf.address(), buf.capacity());
		nioBuf.limit(buf.capacity()).position(buf.wIndex());
		return nioBuf;
	}

	public final DynByteBuf i_getBuffer() {
		return rb;
	}

	/**
	 * 获取Channel用于其它用途
	 */
	public AbstractSelectableChannel i_outOfControl() {
		lock.lock();
		try {
			if (state > CLOSED) throw new IllegalStateException();
			state = CLOSED;
			return ch;
		} finally {
			lock.unlock();
		}
	}

	public boolean isTCP() {
		return true;
	}
	public final boolean isClosePending() {
		return state == CLOSE_PENDING;
	}

	public void invokeLater(Runnable r) {
		TaskPool.CpuMassive().pushTask(() -> {
			lock.lock();
			try {
				r.run();
			} finally {
				lock.unlock();
			}
		});
	}

	public Lock lock() { return lock; }
}
