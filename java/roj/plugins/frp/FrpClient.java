package roj.plugins.frp;

import org.jetbrains.annotations.NotNull;
import roj.collect.IntBiMap;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.net.*;
import roj.net.handler.MSSCrypto;
import roj.net.handler.Timeout;
import roj.net.http.Headers;
import roj.net.http.h2.H2Connection;
import roj.net.http.h2.H2Exception;
import roj.net.http.h2.H2Stream;
import roj.net.mss.MSSContext;
import roj.net.mss.MSSKeyPair;
import roj.text.Escape;
import roj.text.logging.Logger;
import roj.ui.Terminal;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2024/9/15 0015 0:23
 */
public class FrpClient extends FrpCommon implements Consumer<MyChannel> {
	private static final Logger LOGGER = Logger.getLogger();
	private final MSSContext context = new MSSContext();

	public static void main(String[] args) throws Exception {
		var kpg = KeyPairGenerator.getInstance("EdDSA");

		var server = new FrpServer(null);
		server.addLocalRoom(new FrpRoom("NAS"));
		server.setALPN("MFP").setCertificate(new MSSKeyPair(kpg.generateKeyPair()));
		server.launch().bind(12323).launch();

		var host = new FrpServer(null);
		host.addRemoteRoom(new FrpRoom("Host_Test"), new InetSocketAddress(12323));
		host.setALPN("MFP").setCertificate(new MSSKeyPair(kpg.generateKeyPair()));


		var test = new FrpClient();
		test.room = "NAS";
		test.nickname = "Roj234-PC";

		test.context.setALPN("MFP").setCertificate(new MSSKeyPair(kpg.generateKeyPair()));

		ClientLaunch.tcp().initializator(test).connect(null, 12323).launch();

		Terminal.pause();
		System.exit(0);
	}

	public String room, nickname;
	final List<PortMapEntry> portMaps = new SimpleList<>();
	final List<ServerLaunch> listeners = new SimpleList<>();

	// stale timeout = 1m
	IntBiMap<InetSocketAddress> udpSenderIds = new IntBiMap<>();

	public FrpClient() {super(false);}

	@Override
	public void accept(MyChannel ch) {
		ch.addLast("mss", new MSSCrypto(context.clientEngine()))
		  .addLast("timeout", new Timeout(60000, 1000))
		  .addLast("h2", this);
	}

	@Override
	protected void onOpened(ChannelCtx ctx) throws IOException {
		super.onOpened(ctx);

		var connect = new Headers();
		connect.put(":method", "JOIN");
		connect.put(":authority", room);
		connect.put(":path", nickname);
		connect.put(":scheme", "Frp");

		sendHeaderClient(connect, false);
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		super.channelClosed(ctx);
		System.out.println("Client Connection Closed");
		for (var listener : listeners) IOUtil.closeSilently(listener);
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id.equals(Timeout.READ_TIMEOUT) && ping(ping -> System.out.println("新的延迟: "+ping.getRTT()+"ms"))) {
			event.setResult(Event.RESULT_DENY);
		}
	}

	private boolean nextIsUdp;
	protected @NotNull H2Stream newStream(int id) {
		var stream = id == 1 ? new Control() :
			nextIsUdp
				? new UDP(id)
				: new FrpProxy(id);
		initStream(stream);
		return stream;
	}

	private void newTcpListener(PortMapEntry entry) throws IOException {
		var header = new Headers();
		header.put(":method", "CONNECT");
		header.put(":path", entry.name);
		header.put(":scheme", "Frp");

		listeners.add(ServerLaunch.tcp().initializator(ch -> {
			ch.readInactive();

			var lock = channel().channel().lock();
			lock.lock();
			try {
				nextIsUdp = false;
				var proxy = (FrpProxy) sendHeaderClient(header, false);
				proxy.init(ch, entry);
				sendData(proxy, ByteList.EMPTY, false);
			} catch (IOException e) {
				IOUtil.closeSilently(ch);
				LOGGER.warn("TCPListener {} exception", e, entry);
			} finally {
				lock.unlock();
			}
		}).bind(entry.port));
	}

	private void newUdpListener(PortMapEntry entry) throws IOException {
		var header = new Headers();
		header.put(":method", "CONNECT");
		header.put(":path", entry.name);
		header.put(":scheme", "Frp");

		listeners.add(ServerLaunch.udp().initializator(ch -> {
			var lock = channel().channel().lock();
			lock.lock();
			try {
				nextIsUdp = true;
				var proxy = (UDP) sendHeaderClient(header, false);
				proxy.init(ch, entry);
			} catch (IOException e) {
				IOUtil.closeSilently(ch);
				LOGGER.warn("UDPListener {} exception", e, entry);
			} finally {
				lock.unlock();
			}
		}).bind(entry.port));
	}

	public class Control extends FrpCommon.Control {
		@Override
		protected String headerEnd(H2Connection man) {
			Headers head = _header;

			String status = head.getField(":status");
			if (!status.equals("200")) {
				if (status.equals("304")) {
					System.out.println("3端模式重新认证");

					try {
						var ch = man.channel().channel();
						ch.removeAll();
						accept(ch);
						ch.fireOpen();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}

					return null;
				}

				try {
					LOGGER.warn("登录失败({}): {}", status, Escape.decodeURI(head.getField(":error")));
				} catch (MalformedURLException e) {
					throw new RuntimeException(e);
				}

				man.streamError(id, H2Exception.ERROR_OK);
			} else {
				System.out.println(head);
				System.out.println("服务器认证成功");
			}

			return null;
		}

		@Override
		protected void onDataPacket(DynByteBuf buf) {
			switch (buf.readAscii(buf.readUnsignedByte())) {
				case "frp:motd":
					System.out.println("服务器MOTD:"+buf.readUTF(buf.readableBytes()));
				break;
				case "frp:port":
					while (buf.isReadable()) {
						portMaps.add(new PortMapEntry(buf.readChar(), buf.readVUIUTF(), buf.readBoolean()));
					}

					try {
						for (PortMapEntry entry : portMaps) {
							if (entry.udp) {
								newUdpListener(entry);
							} else {
								entry.port = 81;
								newTcpListener(entry);
							}
						}

						for (ServerLaunch listener : listeners) {
							listener.launch();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
			}

		}
	}

	final class UDP extends FrpProxy {
		UDP(int id) {super(id);}

		@Override public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
			//TODO
			var udp = (DatagramPkt) msg;
			// 直接丢包
			if (man.getImmediateWindow(this) < udp.buf.readableBytes() + 2) return;

			var key = new InetSocketAddress(udp.addr, udp.port);
			int senderId = udpSenderIds.getValueOrDefault(key, -1);
			if (senderId < 0) udpSenderIds.putByValue(udpSenderIds.size(), key);

			var buf = ctx.alloc().expandBefore(udp.buf, 3);
			buf.putShort(0, buf.wIndex() - 2);

			Lock lock = man.channel().channel().lock();
			lock.lock();
			try {
				if (man.getImmediateWindow(this) < udp.buf.readableBytes() + 2) return;

				boolean flowControl = man.sendData(this, buf, false);
				// 不应出现才对
				if (flowControl) man.streamError(id, H2Exception.ERROR_FLOW_CONTROL);
			} finally {
				lock.unlock();
				if (buf != udp.buf) BufferPool.reserve(buf);
			}
		}

		private final ByteList buffer = new ByteList();
		@Override protected final String onData(H2Connection man, DynByteBuf buf) throws IOException {
			if (buffer.wIndex() > 0) {
				buffer.put(buf);
				buf = buffer;
			}

			while (buf.isReadable()) {
				int rb = buf.readableBytes() - 2;
				if (rb < 0 || rb < buf.readUnsignedShort(buf.rIndex)) {
					if (buf != buffer) buffer.put(buf);
				} else {
					int count = buf.readUnsignedShort();
					int wp = buf.wIndex();
					buf.wIndex(buf.rIndex+count);
					myOnData(buf);
					buf.wIndex(wp);

					buffer.clear();
				}
			}

			return null;
		}

		private void myOnData(DynByteBuf buf) throws IOException {
			var pkt = new DatagramPkt();
			int senderId = buf.readVUInt();

			var address = (InetSocketAddress) udpSenderIds.get(senderId);
			if (address != null) {
				pkt.addr = address.getAddress();
				pkt.port = address.getPort();
				pkt.buf = buf;
				connection.fireChannelWrite(pkt);
			}
		}
	}
}
