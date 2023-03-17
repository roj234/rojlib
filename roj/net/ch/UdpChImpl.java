package roj.net.ch;

import roj.io.buf.BufferPool;
import roj.net.ch.handler.PacketMerger;
import roj.reflect.DirectAccessor;
import roj.util.DynByteBuf;

import java.io.IOException;
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
		Class<?> type = null;
		try {
			type = InetSocketAddress.class.getDeclaredField("holder").getType();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		}

		UdpUtil = DirectAccessor
			.builder(H.class)
			.access(InetSocketAddress.class, "holder", "getHolder", null)
			.access(type, new String[]{"address","port"}, null, new String[]{"getAddress","getPort"})
			.build();
	}
	private static void setAddress(InetSocketAddress addr, InetAddress a, int b) {
		Object c = UdpUtil.getHolder(addr);
		UdpUtil.setAddress(c, a);
		UdpUtil.setPort(c, b);
	}

	private static final int UDP_MAX_SIZE = 65507;

	private final InetSocketAddress nioAddr = new InetSocketAddress(0);
	private final DatagramPkt first = new DatagramPkt();

	private DatagramChannel dc;

	int buffer;

	UdpChImpl(int buffer) throws IOException {
		this(DatagramChannel.open(), buffer);
		ch.configureBlocking(false);
		state = 0;
	}
	UdpChImpl(DatagramChannel server, int buffer) {
		if (buffer > UDP_MAX_SIZE) buffer = UDP_MAX_SIZE;

		ch = dc = server;
		state = MyChannel.CONNECTED;
		this.buffer = buffer;
	}

	@Override
	public void register(Selector sel, int ops, Object att) throws IOException {
		lock.lock();
		try {
			if (state == CONNECT_PENDING) ops |= SelectionKey.OP_CONNECT;
			if ((flag & READ_INACTIVE) != 0) ops &= ~SelectionKey.OP_READ;
		} finally {
			lock.unlock();
		}
		key = ch.register(sel, ops, att);
	}

	@Override
	protected boolean connect0(InetSocketAddress na) throws IOException {
		dc.connect(na);
		addFirst("_UDP_AddressProvider", new AddressBinder(na));
		return true;
	}

	static final class AddressBinder extends PacketMerger implements ChannelHandler {
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
	protected SocketAddress finishConnect0() throws IOException {
		return dc.getRemoteAddress();
	}

	@Override
	protected void disconnect0() throws IOException {
		dc.close();
		ch = dc = DatagramChannel.open();
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
		if (pending.isEmpty()||state>=CLOSED) return;

		BufferPool bp = alloc();
		lock.lock();
		try {
			do {
				DatagramPkt p = (DatagramPkt) pending.peekFirst();
				if (p == null) break;

				write1(p, p.buf);

				if (p.buf.isReadable()) break;

				pending.pollFirst();
				bp.reserve(p.buf);
			} while (true);

			if (pending.isEmpty()) {
				flag &= ~PAUSE_FOR_FLUSH;
				key.interestOps(SelectionKey.OP_READ);

				fireWriteDone();
			}
		} finally {
			lock.unlock();
		}
	}

	private void write1(DatagramPkt p, DynByteBuf buf) throws IOException {
		ByteBuffer nioBuffer = syncNioWrite(buf);
		setAddress(nioAddr, p.addr, p.port);
		int w = dc.send(nioBuffer, nioAddr);
		buf.rIndex = nioBuffer.position();
	}

	protected void read() throws IOException {
		while (true) {
			BufferPool bp = alloc();
			DynByteBuf buf = bp.buffer(true, buffer);

			ByteBuffer nioBuffer = syncNioRead(buf);
			InetSocketAddress r = (InetSocketAddress) dc.receive(nioBuffer);
			buf.wIndex(nioBuffer.position());

			if (r == null) break;

			try {
				first.buf = buf;
				first.addr = r.getAddress();
				first.port = r.getPort();
				fireChannelRead(first);
			} finally {
				bp.reserve(buf);
			}
		}
	}

	protected void write(Object o) throws IOException {
		BufferPool bp = alloc();

		DatagramPkt p = (DatagramPkt) o;
		DynByteBuf buf = p.buf;
		if (buf.readableBytes() > UDP_MAX_SIZE) throw new IOException("packet too large");
		if (!buf.isDirect()) buf = bp.buffer(true, buf.readableBytes()).put(buf);

		try {
			write1(p, buf);

			if (buf.isReadable()) {
				if (pending.isEmpty()) key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);

				Object o1 = pending.ringAddLast(new DatagramPkt(p, bp.buffer(true, buf.readableBytes()).put(buf)));
				if (o1 != null) throw new IOException("上层发送缓冲区过载");
			} else {
				fireWriteDone();
			}
		} finally {
			if (p.buf != buf) bp.reserve(buf);

			buf = p.buf;
			buf.rIndex = buf.wIndex();
		}
	}

	public boolean isTCP() { return false; }
}
