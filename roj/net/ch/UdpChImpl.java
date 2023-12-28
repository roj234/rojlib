package roj.net.ch;

import roj.asm.type.TypeHelper;
import roj.io.buf.BufferPool;
import roj.net.ch.handler.PacketMerger;
import roj.reflect.DirectAccessor;
import roj.reflect.ReflectionUtils;
import roj.text.logging.Logger;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

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
	}
	static {
		H inst;
		try {
			Class<?> type = InetSocketAddress.class.getDeclaredField("holder").getType();
			DirectAccessor<H> b = DirectAccessor.builder(H.class).i_access("java/net/InetSocketAddress", "holder", TypeHelper.class2type(type), "getHolder", null, false);
			Field field = ReflectionUtils.checkFieldName(type, "address", "addr");
			inst = b.access(type, new String[]{field.getName(),"port"}, null, new String[]{"setAddress","setPort"}).build();
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
	protected boolean connect0(InetSocketAddress na) throws IOException {
		dc.connect(na);
		addFirst("_UDP_AddressProvider", new AddressBinder(na));
		return true;
	}

	static final class AddressBinder extends PacketMerger {
		InetAddress addr;
		int port;

		public AddressBinder(InetSocketAddress address) {
			addr = address.getAddress();
			port = address.getPort();
		}

		@Override
		public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
			ctx.channelWrite(new DatagramPkt(addr,port,(DynByteBuf) msg));
		}

		@Override
		public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
			DatagramPkt pkt = (DatagramPkt) msg;
			if (pkt.addr != addr || pkt.port != port) {
				System.out.println("ignored packet " + pkt);
				return;
			}
			mergedRead(ctx, pkt.buf);
		}
	}

	@Override
	protected SocketAddress finishConnect0() throws IOException { return dc.getRemoteAddress(); }
	@Override
	protected void closeGracefully0() throws IOException { close(); }
	@Override
	protected void disconnect0() throws IOException { dc.disconnect(); }

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

		BufferPool bp = alloc();
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
				flag &= ~PAUSE_FOR_FLUSH;
				key.interestOps(SelectionKey.OP_READ);

				fireFlushed();
			}
		} finally {
			lock.unlock();
		}
	}

	private void write1(DatagramPkt p, DynByteBuf buf) throws IOException {
		ByteBuffer nioBuffer = syncNioWrite(buf);
		InetSocketAddress address = setAddress(nioAddr, p.addr, p.port);
		int w = dc.send(nioBuffer, address);
		buf.rIndex = nioBuffer.position();
	}

	protected void read() throws IOException {
		while (state == OPENED && dc.isOpen()) {
			DynByteBuf buf = alloc().allocate(true, UDP_MAX_SIZE, 0);
			try {
				ByteBuffer nioBuffer = syncNioRead(buf);
				InetSocketAddress r = (InetSocketAddress) dc.receive(nioBuffer);
				buf.wIndex(nioBuffer.position());
				BufferPool.expand(buf, buf.wIndex()-buf.capacity());

				if (r == null) break;

				first.buf = buf;
				first.addr = r.getAddress();
				first.port = r.getPort();
				fireChannelRead(first);
			} finally {
				BufferPool.reserve(buf);
			}
		}
	}

	protected void write(Object o) throws IOException {
		BufferPool bp = alloc();

		DatagramPkt p = (DatagramPkt) o;
		DynByteBuf buf = p.buf;
		if (buf.readableBytes() > UDP_MAX_SIZE) throw new IOException("packet too large");
		if (!buf.isDirect()) buf = bp.allocate(true, buf.readableBytes(), 0).put(buf);

		try {
			write1(p, buf);

			if (buf.isReadable()) {
				if (pending.isEmpty()) key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);

				Object o1 = pending.ringAddLast(new DatagramPkt(p, bp.allocate(true, buf.readableBytes(), 0).put(buf)));
				if (o1 != null) throw new IOException("上层发送缓冲区过载");
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