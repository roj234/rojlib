package roj.plugins.frp;

import org.jetbrains.annotations.NotNull;
import roj.collect.IntBiMap;
import roj.http.Headers;
import roj.http.HttpHead;
import roj.http.h2.H2Connection;
import roj.http.h2.H2Exception;
import roj.http.h2.H2Stream;
import roj.io.BufferPool;
import roj.io.IOUtil;
import roj.net.*;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.text.Tokenizer;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.function.ExceptionalRunnable;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.locks.Lock;

/**
 * @author Roj234
 * @since 2024/9/15 0:23
 */
class FrpServerConnection extends FrpCommon {
	public FrpServer server;

	public FrpServerConnection(FrpServer server) {
		super(true);
		this.server = server;
	}

	public String name, addr;
	public long time = System.currentTimeMillis();

	public FrpRoom room;
	public Control control;

	private IntBiMap<MyChannel> udpSenderIds = new IntBiMap<>();

	@Override
	public String toString() {
		var sb = new CharList();
		sb.append(TextUtil.bytes2hex(hash), 0, 8);
		if (name != null) Tokenizer.escape(sb.append("(\""), name).append("\")");
		sb.append(" on ").append(addr);
		return sb.toStringAndFree();
	}

	@Override
	public void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {
		ex.printStackTrace();
		super.exceptionCaught(ctx, ex);
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		super.channelClosed(ctx);
		for (MyChannel value : udpSenderIds.values()) {
			IOUtil.closeSilently(value);
		}
		server.exit(this);
	}

	protected @NotNull H2Stream newStream(int id) {
		var stream = id == 1 ? control = new Control() : (id&1) != 0 ? new TCP(id) : new FrpProxy(id);
		initStream(stream);
		return stream;
	}

	public class Control extends FrpCommon.Control {
		private final ArrayDeque<ExceptionalRunnable<RuntimeException>> tasks = new ArrayDeque<>();

		@Override
		protected void tick(H2Connection man) throws IOException {
			if (!tasks.isEmpty()) synchronized (tasks) {
				for (var task : tasks) task.run();
				tasks.clear();
			}
		}

		public void schedule(ExceptionalRunnable<?> runnable) {
			synchronized (tasks) {tasks.add(Helpers.cast(runnable));}
		}

		@Override
		protected String headerEnd(H2Connection man) throws IOException {
			name = _header.header(":authority");
			addr = Net.toString(man.channel().remoteAddress());

			FrpServer.LOGGER.info("{} {} 上线了", room.remote != FrpServerConnection.this ? "用户" : "主机", FrpServerConnection.this);

			var result = new Headers();
			result.put(":status", "200");
			result.put("server", Constants.PROTOCOL_VERSION);
			man.sendHeader(this, result, false);

			String motd = room.motd;
			DynByteBuf data = IOUtil.getSharedByteBuf().putShort(0).put(8).putAscii("frp:motd").putUTFData(motd);
			man.sendData(this, data.setShort(0, data.readableBytes()-2), false);

			if (room.remote != FrpServerConnection.this) {
				data.clear();
				data.putShort(0).put(8).putAscii("frp:port");
				for (var entry : room.portMaps) {
					data.putShort(entry.port).putVUIUTF(entry.name).putBool(entry.udp);
				}
				man.sendData(this, data.setShort(0, data.readableBytes()-2), false);
				System.out.println("Send PortMap");
			} else {
				room.ready = true;
			}

			return null;
		}

		@Override
		protected void onDataPacket(DynByteBuf buf) {

		}

		@Override
		protected void onFinish(H2Connection man) {
			super.onFinish(man);
			FrpServer.LOGGER.info("{} {} 下线了", room.remote != FrpServerConnection.this ? "用户" : "主机", FrpServerConnection.this);
		}
	}

	static final class TCP extends FrpProxy {
		TCP(int id) {super(id);}

		@Override protected void onHeaderDone(H2Connection man1, HttpHead head, boolean hasData) {
			var man = (FrpServerConnection) man1;

			var port = man.room.portMaps.get(head.getPath());
			if (port == null) {
				man.streamError(id, 1002);
				return;
			}

			// udp转发流程 客户端FrpUdpProxy.Client => NewStream =>
			if (port.udp) {
				// 增加2字节的长度头分割，之后通过每个Connection一个的UDPChannel发送至目标UDP端口
				// 收到数据时，同样根据来源端口不同，转发到不同的Stream
				// 这些Stream不应当意外关闭
			}

			this.man = man;
			this.port = port;

			FrpServer.LOGGER.info("流{}正在开启", this);
			try {
				var c = ClientLaunch.tcp().loop(loop).connect(port.address, port.port).timeout(15000);
				init(c.channel(), port);
				c.launch();
			} catch (Exception e) {
				FrpServer.LOGGER.warn("流{}开启失败", e, this);
				man.streamError(id, 1003);
			}
		}

		@Override
		public void channelOpened(ChannelCtx ctx) throws IOException {
			FrpServer.LOGGER.info("流{}开启成功", this);
			Lock lock = man.channel().channel().lock();
			lock.lock();
			try {
				man.sendHeader(this, OK, false);
				man.sendData(this, ByteList.EMPTY, false);
			} finally {
				lock.unlock();
			}
		}
	}

	final class UDP extends FrpCommon.Control implements ChannelHandler {
		PortMapEntry port;
		private ByteList sendPending;

		UDP(int id) {super(id);}

		@Override protected String headerEnd(H2Connection man) {return null;}

		@Override public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
			int senderId = udpSenderIds.getByValue(ctx.channel());
			if (senderId < 0) return;

			//TODO
			var udp = (DatagramPkt) msg;
			// 直接丢包
			if (getImmediateWindow(this) < udp.data.readableBytes() + 2) return;

			var key = udp.address;

			var buf = ctx.alloc().expandBefore(udp.data, 3);
			buf.setShort(0, buf.wIndex() - 2);
			buf.putVUInt(senderId);

			Lock lock = channel().channel().lock();
			lock.lock();
			try {
				if (getImmediateWindow(this) < udp.data.readableBytes() + 2) return;

				boolean flowControl = sendData(this, buf, false);
				// 不应出现才对
				if (flowControl) streamError(id, H2Exception.ERROR_FLOW_CONTROL);
			} finally {
				lock.unlock();
				if (buf != udp.data) BufferPool.reserve(buf);
			}
		}

		@Override
		protected void onDataPacket(DynByteBuf buf) {
			var pkt = new DatagramPkt();
			int senderId = buf.readVUInt();

			var address = (MyChannel) udpSenderIds.get(senderId);
			if (address == null) {
				// create new sender, add all UDP handler
				//   (filter pkt via sender port == PortMap.port)
			}

			pkt.setAddress(port.address, port.port);
			pkt.data = buf;
			try {
				address.fireChannelWrite(pkt);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void success(FrpRoom room) {this.room = room;}
	public void fail(String s) {Helpers.athrow(new IllegalStateException(s));}
}
