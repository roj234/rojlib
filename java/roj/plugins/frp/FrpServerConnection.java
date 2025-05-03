package roj.plugins.frp;

import org.jetbrains.annotations.NotNull;
import roj.collect.IntBiMap;
import roj.config.Tokenizer;
import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.net.*;
import roj.net.http.Headers;
import roj.net.http.HttpHead;
import roj.net.http.h2.H2Connection;
import roj.net.http.h2.H2Exception;
import roj.net.http.h2.H2Stream;
import roj.text.TextUtil;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetSocketAddress;
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

	public byte[] hash = ArrayCache.BYTES;
	public String name, addr;
	public long time = System.currentTimeMillis();

	public FrpRoom room;
	public H2Stream control;

	private IntBiMap<MyChannel> udpSenderIds = new IntBiMap<>();

	@Override
	public String toString() {return "\""+Tokenizer.addSlashes(name)+"\"/"+TextUtil.bytes2hex(hash).substring(0,8)+" IP:"+addr;}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		super.channelClosed(ctx);
		for (MyChannel value : udpSenderIds.values()) {
			IOUtil.closeSilently(value);
		}
		server.exit(this);
	}

	protected @NotNull H2Stream newStream(int id) {
		var stream = id == 1 ? control = new Control() : new TCP(id);
		initStream(stream);
		return stream;
	}

	public class Control extends FrpCommon.Control {
		@Override
		protected String headerEnd(H2Connection man) throws IOException {
			Headers head = _header;

			name = head.getField(":path");
			addr = NetUtil.toString((InetSocketAddress) man.channel().remoteAddress());

			Headers result = new Headers();
			result.put("server", Constants.PROTOCOL_VERSION);

			if (!server.join(FrpServerConnection.this, head, result)) {
				FrpServer.LOGGER.info("客户端 {} 被拒绝连接: {}", FrpServerConnection.this, result.get(":error"));

				result.putIfAbsent(":status", "403");
				man.goaway(H2Exception.ERROR_REFUSED, "登录失败");
				man.sendHeader(this, result, true);
				return null;
			}

			// end-to-end的话就用这个stream => embedded channel传输数据重新握手
			// 也可以关掉不对称加密而开启IP Forwarding
			result.putIfAbsent(":status", "200");
			man.sendHeader(this, result, false);

			if (result.getField(":status").equals("304")) {
				FrpServer.LOGGER.info("正在启用端到端加密", FrpServerConnection.this);

				var ch = man.channel().channel();
				ch.removeAll();
				room.addRemoteConnection(ch);
				return null;
			}

			FrpServer.LOGGER.info("客户端 {} 上线了", FrpServerConnection.this);

			var data = IOUtil.getSharedByteBuf().putShort(0).put(8).putAscii("frp:motd").putUTFData(room.motd);
			man.sendData(this, data.putShort(0, data.readableBytes()-2), false);

			data.clear();
			data.putShort(0).put(8).putAscii("frp:port");
			for (var entry : room.portMaps) {
				data.putShort(entry.port).putVUIUTF(entry.name).putBool(entry.udp);
			}
			man.sendData(this, data.putShort(0, data.readableBytes()-2), false);

			return null;
		}

		@Override
		protected void onDataPacket(DynByteBuf buf) {

		}

		@Override
		protected void onFinish(H2Connection man) {
			super.onFinish(man);
			FrpServer.LOGGER.info("客户端 {} 下线了", FrpServerConnection.this);
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
			int senderId = udpSenderIds.getInt(ctx.channel());
			if (senderId < 0) return;

			//TODO
			var udp = (DatagramPkt) msg;
			// 直接丢包
			if (getImmediateWindow(this) < udp.buf.readableBytes() + 2) return;

			var key = new InetSocketAddress(udp.addr, udp.port);

			var buf = ctx.alloc().expandBefore(udp.buf, 3);
			buf.putShort(0, buf.wIndex() - 2);
			buf.putVUInt(senderId);

			Lock lock = channel().channel().lock();
			lock.lock();
			try {
				if (getImmediateWindow(this) < udp.buf.readableBytes() + 2) return;

				boolean flowControl = sendData(this, buf, false);
				// 不应出现才对
				if (flowControl) streamError(id, H2Exception.ERROR_FLOW_CONTROL);
			} finally {
				lock.unlock();
				if (buf != udp.buf) BufferPool.reserve(buf);
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

			pkt.addr = port.address;
			pkt.port = port.port;
			pkt.buf = buf;
			try {
				address.fireChannelWrite(pkt);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
