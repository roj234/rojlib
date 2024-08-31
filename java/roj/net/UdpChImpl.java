package roj.net;

import roj.asm.type.TypeHelper;
import roj.io.buf.BufferPool;
import roj.reflect.Bypass;
import roj.reflect.ReflectionUtils;
import roj.text.logging.Logger;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;

/**
 * @author Roj233
 * @since 2022/5/18 0:00
 */
class UdpChImpl extends MyChannel {
	private static final H UdpUtil;
	private interface H {
		Object getHolder(Object o);
		void setAddress(Object holder, Object address);
		void setPort(Object holder, int port);
		void removeKey(AbstractSelectableChannel ch, SelectionKey key);
	}
	static {
		H inst;
		try {
			Class<?> type = InetSocketAddress.class.getDeclaredField("holder").getType();
			Bypass<H> b = Bypass.builder(H.class).i_access("java/net/InetSocketAddress", "holder", TypeHelper.class2type(type), "getHolder", null, false);
			Field field = ReflectionUtils.checkFieldName(type, "address", "addr");
			inst = b.access(type, new String[]{field.getName(),"port"}, null, new String[]{"setAddress","setPort"}).delegate(AbstractSelectableChannel.class, "removeKey").build();
		} catch (Exception e) {
			Logger.getLogger().error("UdpChImpl.SocketAddressHelper初始化错误", e);
			inst = null;
		}
		UdpUtil = inst;
	}
	private static InetSocketAddress setAddress(InetSocketAddress addr, InetAddress a, int b) {
		if (UdpUtil == null) return new InetSocketAddress(a, b);

		Object c = UdpUtil.getHolder(addr);
		UdpUtil.setAddress(c, a);
		UdpUtil.setPort(c, b);
		return addr;
	}

	static final int UDP_MAX_SIZE = 65507;

	private final InetSocketAddress nioAddr = new InetSocketAddress(0);
	private final DatagramPkt first = new DatagramPkt();

	private final DatagramChannel dc;

	UdpChImpl(DatagramChannel server) throws IOException {
		ch = dc = server;
		ch.configureBlocking(false);
	}

	@Override
	public void register(Selector sel, int ops, Object att) throws IOException {
		lock.lock();
		try {
			if ((flag & READ_INACTIVE) != 0) ops &= ~SelectionKey.OP_READ;
		} finally {
			lock.unlock();
		}
		key = dc.register(sel, ops, att);
	}

	@Override
	protected void bind0(InetSocketAddress na) throws IOException { dc.bind(na); }
	@Override
	protected boolean connect0(InetSocketAddress na) throws IOException {dc.connect(na);return true;}
	@Override
	protected SocketAddress finishConnect0() throws IOException { return dc.getRemoteAddress(); }
	@Override
	protected void closeGracefully0() throws IOException { close(); }
	@Override
	protected void disconnect0() throws IOException {
		dc.disconnect();
		UdpUtil.removeKey(dc, key);
	}

	@Override
	public SocketAddress remoteAddress() {
		try {
			return dc.getRemoteAddress();
		} catch (IOException e) {
			return null;
		}
	}

	public void flush() throws IOException {
		if (state >= CLOSED) return;
		fireFlushing();
		if (pending.isEmpty()) return;

		lock.lock();
		try {
			do {
				DatagramPkt p = (DatagramPkt) pending.peekFirst();
				if (p == null) break;

				write1(p, p.buf);

				if (p.buf.isReadable()) break;

				pending.pollFirst();
				BufferPool.reserve(p.buf);
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

	private void write1(DatagramPkt p, DynByteBuf buf) throws IOException {
		var nioBuffer = syncNioWrite(buf);
		var address = setAddress(nioAddr, p.addr, p.port);
		// buf.readableBytes() or 0
		dc.send(nioBuffer, address);
		buf.rIndex = nioBuffer.position();
	}

	protected void read() throws IOException {
		while (state == OPENED) {
			var buf = alloc().allocate(true, UDP_MAX_SIZE, 0);
			try {
				var nioBuffer = syncNioRead(buf);
				var addr = (InetSocketAddress) dc.receive(nioBuffer);
				if (addr == null) break;

				buf.wIndex(nioBuffer.position());
				alloc().expand(buf, buf.wIndex()-buf.capacity());

				first.buf = buf;
				first.addr = addr.getAddress();
				first.port = addr.getPort();
				fireChannelRead(first);
			} finally {
				BufferPool.reserve(buf);
			}

			if ((flag&READ_INACTIVE) != 0) break;
		}
	}

	protected void write(Object o) throws IOException {
		var bp = alloc();

		var p = (DatagramPkt) o;
		var buf = p.buf;
		if (buf.readableBytes() > UDP_MAX_SIZE) throw new IOException("packet too large");

		try {
			if (pending.isEmpty()) {
				if (!buf.isDirect()) buf = bp.allocate(true, buf.readableBytes(), 0).put(buf);
				write1(p, buf);
			}

			if (buf.isReadable()) {
				if (pending.isEmpty()) {
					fireFlushing();
					key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
				} else if (pending.size() > 5) {
					pauseAndFlush();
				}

				DynByteBuf put = bp.allocate(true, buf.readableBytes(), 0).put(buf);
				if (!pending.offerLast(new DatagramPkt(p, put))) {
					BufferPool.reserve(put);
					throw new IOException("上层发送缓冲区过载");
				}
			} else {
				fireFlushed();
			}
		} finally {
			if (p.buf != buf) BufferPool.reserve(buf);

			buf = p.buf;
			buf.rIndex = buf.wIndex();
		}
	}

	public boolean isTCP() { return false; }
}