package roj.plugins.frp;

import org.jetbrains.annotations.NotNull;
import roj.collect.CharMap;
import roj.collect.IntBiMap;
import roj.collect.ArrayList;
import roj.crypt.Blake3;
import roj.crypt.KeyType;
import roj.http.Headers;
import roj.http.h2.H2Connection;
import roj.http.h2.H2Exception;
import roj.http.h2.H2Stream;
import roj.io.IOUtil;
import roj.io.BufferPool;
import roj.net.*;
import roj.net.mss.MSSHandler;
import roj.net.handler.Timeout;
import roj.net.mss.MSSContext;
import roj.net.mss.MSSException;
import roj.net.mss.MSSKeyPair;
import roj.net.mss.MSSPublicKey;
import roj.text.CharList;
import roj.text.URICoder;
import roj.text.Formatter;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.ui.TUI;
import roj.ui.Tty;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2024/9/15 0:23
 */
public class FrpClient extends FrpCommon implements Consumer<MyChannel> {
	private static final Logger LOGGER = Logger.getLogger();
	private final MSSContext context = new MSSContext() {
		@Override
		protected MSSPublicKey checkCertificate(Object ctx, int type, DynByteBuf data, boolean isServerCert) throws MSSException, GeneralSecurityException {
			var key = super.checkCertificate(ctx, type, data, isServerCert);

			var digest = new Blake3(32);
			digest.update(data.slice());
			byte[] cfp = digest.digestShared();

			needDoubleNegotiation = !Arrays.equals(cfp, roomHash);
			System.out.println("needDoubleNegotiation = "+needDoubleNegotiation);
			return key;
		}

		@Override
		protected boolean processExtensions(Object ctx, CharMap<DynByteBuf> extIn, CharMap<DynByteBuf> extOut, int stage) throws MSSException {
			if (stage == 2) extOut.put(FrpServer.EXTENSION_TARGET_ROOM, DynByteBuf.wrap(roomHash));
			return super.processExtensions(ctx, extIn, extOut, stage);
		}
	};


	public boolean needDoubleNegotiation;

	public static void main(String[] args) throws Exception {
		Formatter f;
		if (Tty.IS_RICH) {
			f = (env, sb) -> {
				((BiConsumer<Object, CharList>) env.get("0")).accept(env, sb.append('['));

				Level level = (Level) env.get("LEVEL");
				sb.append("]\u001b[").append(level.color).append("m[").append(env.get("NAME"));
				if (level.ordinal() > Level.WARN.ordinal())
					sb.append("][").append(env.get("THREAD"));

				return sb.append("]\u001b[0m: ");
			};
		} else {
			f = Formatter.simple("[${0}][${THREAD}][${NAME}/${LEVEL}]: ");
		}
		Logger.getRootContext().setPrefix(f);

		var role = args[0];

		KeyType kp = KeyType.getInstance("EdDSA");

		MSSKeyPair server_key = new MSSKeyPair(kp.loadOrGenerateKey(new File("server_key.bin"), new byte[0]));
		MSSKeyPair host_key = new MSSKeyPair(kp.loadOrGenerateKey(new File("host_key.bin"), new byte[0]));
		MSSKeyPair client_key = new MSSKeyPair(kp.loadOrGenerateKey(new File("client_key.bin"), new byte[0]));

		if (role.equals("server")) {

			var server = new FrpServer(null);
			server.setALPN("MFP").setCertificate(server_key);
			FrpRoom server_Room = server.addLocalRoom();
			server_Room.ready = true;
			server.launch().bind(12323).launch();
		}

		if (role.equals("host")) {
			var host = new FrpServer(null);
			host.setALPN("MFP").setCertificate(host_key);
			FrpRoom host_Room = host.addRemoteRoom(new FrpRoom("Host_Test"), new InetSocketAddress(12323));
			host_Room.ready = true;
		}

		if (role.equals("client")) {
			var test = new FrpClient();

			var digest = new Blake3(32);
			digest.update(server_key.encode());

			test.roomHash = digest.digestShared();
			test.friendlyRoom = "roomName";
			test.friendlyName = "Roj234-PC";

			test.context.setALPN("MFP").setCertificate(client_key);
			ClientLaunch.tcp().initializator(test).connect(null, 12323).launch();
		}

		TUI.pause();
		System.exit(0);
	}

	public byte[] roomHash;
	public String friendlyRoom, friendlyName;
	final List<PortMapEntry> portMaps = new ArrayList<>();
	final List<ServerLaunch> listeners = new ArrayList<>();

	// stale timeout = 1m
	IntBiMap<InetSocketAddress> udpSenderIds = new IntBiMap<>();

	public FrpClient() {super(false);}

	@Override
	public void accept(MyChannel ch) {
		ch.addLast("mss", new MSSHandler(context.clientEngine()))
		  .addLast("timeout", new Timeout(2000, 1000))
			.addLast("double_negotiation_check", new ChannelHandler() {
				@Override
				public void channelOpened(ChannelCtx ctx) throws IOException {
					if (needDoubleNegotiation) {
						ctx.flush();
						System.out.println("Start double negotiation");
						ctx.channel().replace("mss", new MSSHandler(context.clientEngine()));
						ctx.channel().fireOpen();
					} else {
						ctx.channelOpened();
					}
					ctx.removeSelf();
				}
			})
		  .addLast("h2", this);
	}

	@Override
	public void onOpened() throws IOException {
		var header = new Headers();
		header.put(":authority", friendlyName);
		header.put(":scheme", "Frp");
		sendHeaderClient(header, false);
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

			String status = head.header(":status");
			if (!status.equals("200")) {
				try {
					LOGGER.warn("登录失败({}): {}", status, URICoder.decodeURI(head.header(":error")));
				} catch (MalformedURLException e) {
					throw new RuntimeException(e);
				}

				man.streamError(id, H2Exception.ERROR_OK);
			} else {
				LOGGER.info("客户端登录成功: ILFrp/"+head.header("server"));
			}

			return null;
		}

		@Override
		protected void onDataPacket(DynByteBuf buf) {
			switch (buf.readAscii(buf.readUnsignedByte())) {
				case "frp:motd":
					System.out.println("房间欢迎消息:"+buf.readUTF(buf.readableBytes()));
				break;
				case "frp:port":
					while (buf.isReadable()) {
						portMaps.add(new PortMapEntry(buf.readChar(), buf.readVUIUTF(), buf.readBoolean()));
					}

					System.out.println(portMaps);
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
			if (man.getImmediateWindow(this) < udp.data.readableBytes() + 2) return;

			var key = udp.address;
			int senderId = udpSenderIds.getByValueOrDefault(key, -1);
			if (senderId < 0) udpSenderIds.putByValue(key, udpSenderIds.size());

			var buf = ctx.alloc().expandBefore(udp.data, 3);
			buf.putShort(0, buf.wIndex() - 2);

			Lock lock = man.channel().channel().lock();
			lock.lock();
			try {
				if (man.getImmediateWindow(this) < udp.data.readableBytes() + 2) return;

				boolean flowControl = man.sendData(this, buf, false);
				// 不应出现才对
				if (flowControl) man.streamError(id, H2Exception.ERROR_FLOW_CONTROL);
			} finally {
				lock.unlock();
				if (buf != udp.data) BufferPool.reserve(buf);
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
				pkt.address = address;
				pkt.data = buf;
				connection.fireChannelWrite(pkt);
			}
		}
	}
}
