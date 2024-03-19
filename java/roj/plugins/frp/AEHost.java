package roj.plugins.frp;

import roj.collect.IntBiMap;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.config.Tokenizer;
import roj.io.IOUtil;
import roj.net.NetUtil;
import roj.net.ch.*;
import roj.net.handler.MSSCipher;
import roj.net.handler.Pipe2;
import roj.net.handler.Timeout;
import roj.net.handler.VarintSplitter;
import roj.net.mss.MSSEngineServer;
import roj.plugins.frp.server.AEServer;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.text.logging.Level;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetAddress;

/**
 * @author Roj233
 * @since 2023/11/02 9:23
 */
public class AEHost extends IAEClient {
	final IntMap<Client> clients = new IntMap<>();
	MyHashMap<byte[], String> whitelist;

	public AEHost(SelectorLoop loop) {super(loop);}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		onlyOneMissed = true;

		DynByteBuf rb = (DynByteBuf) msg;
		switch (rb.readUnsignedByte()) {
			case P___HEARTBEAT: rb.readLong(); break;
			case P___LOGOUT: ctx.close(); break;
			case P___OBSOLETED_3: int id; Pipe2 pair; break;
			case PHH_CLIENT_REQUEST_CHANNEL:
				int clientId = rb.readInt();
				int portId = rb.readUnsignedByte();
				byte[] sessionId = rb.readBytes(16);

				ByteList b = new ByteList(); // AEServer.server on local mode also uses IOUtil.getSharedByteBuf()
				Client client = clients.get(clientId);

				String error;
				if (client == null) {
					error = "未知的客户端";
				} else if (portId >= portMap.length) {
					error = "无效的管道ID (服务端验证错误？)";
				} else if (pipes.size() >= HOST_MAX_PIPES) {
					error = "Host开启的管道过多("+pipes.size()+" > "+HOST_MAX_PIPES+")";
				} else if (portId >= udpPortMap && !client.checkUdp(portMap[portId])) {
					error = "重复的UDP管道";
				} else {
					PipeInfoClient att = new PipeInfoClient(clientId, 0, portId);

					MSSEngineServer engine = new MSSEngineServer();
					engine.setDefaultCert(client_factory.getKeyPair());
					engine.switches(MSSEngineServer.VERIFY_CLIENT);

					block:
					if (AEServer.server != null) {
						MyChannel lp = AEServer.server.addLocalPipe(ctx, DynByteBuf.wrap(sessionId));
						if (lp == null) {
							System.out.println("Error LocalPipe IsNull "+DynByteBuf.wrap(sessionId).hex());
							break block;
						}
						lp.addFirst("tls", new MSSCipher(engine))
						  .addLast("handshake", new GetPipeH(client, att, null, engine, portId));
					} else {
						ClientLaunch bs = ClientLaunch.tcp().loop(loop).connect(server);
						bs.channel()
						  .addLast("cipher", new MSSCipher(client_factory.get()))
						  .addLast("splitter", VarintSplitter.twoMbVLUI())
						  .addLast("timeout", new Timeout(2000))
						  .addLast("handshake", new GetPipeH(client, att, sessionId, engine, portId));
						bs.launch();
					}

					break;
				}

				rb.rIndex = rb.wIndex();
				LOGGER.info("客户端 #{} 开启管道被阻止: {}", clientId, error);
				ctx.channelWrite(b.put(PHS_CHANNEL_DENY).put(sessionId).putVUIGB(error));
			break;
			case PHH_CLIENT_LOGIN:
				clientId = rb.readInt();

				byte[] digest = rb.readBytes(DIGEST_LENGTH);
				String name = rb.readVUIGB();
				String ip = NetUtil.bytes2ip(rb.readBytes(rb.readableBytes()));

				client = new Client(digest, name, ip);
				if (whitelist != null && !whitelist.containsKey(digest)) {
					LOGGER.warn("客户端 #{} ({}) 不在白名单内.", clientId, client);
					kickSome(clientId);
					break;
				}
				clients.putInt(clientId, client);

				LOGGER.info("客户端 #{} ({}) 上线了.", clientId, client);
			break;
			case PHH_CLIENT_LOGOUT:
				clientId = rb.readInt();
				client = clients.remove(clientId);
				if (client != null) {
					client.close();
					LOGGER.info("客户端 #{} ({}) 下线了.", clientId, client);
				}
			break;
			default: unknownPacket(ctx, rb); break;
		}

		if (!LOGGER.getLevel().canLog(Level.DEBUG)) rb.rIndex = rb.wIndex();
	}
	private class GetPipeH implements ChannelHandler {
		private final Client client;
		private final PipeInfoClient att;
		private final byte[] sessionId;
		private final MSSEngineServer engine;
		private final int portId;

		private byte stage;

		public GetPipeH(Client client, PipeInfoClient att, byte[] sessionId, MSSEngineServer engine, int portId) {
			this.client = client;
			this.att = att;
			this.sessionId = sessionId;
			this.engine = engine;
			this.portId = portId;
			this.stage = (byte) (sessionId == null ? 1 : 0);
		}

		@Override
		public void channelOpened(ChannelCtx ctx) throws IOException {
			if (stage == 0) {
				ctx.channelWrite(IOUtil.getSharedByteBuf().put(PPS_PIPE_HOST).put(sessionId));
				ctx.flush();
				ctx.channel().handler("splitter").removeSelf();

				ctx.channel().handler("cipher").replaceSelf(new MSSCipher(engine));
				return;
			}

			ctx.removeSelf();
			stage = 1;
			ctx.channelWrite(IOUtil.getSharedByteBuf().put(portId));

			Pipe2 pipe = new Pipe2(ctx.channel(), true);
			pipe.att = att;

			try {
				char port = portMap[att.portId];
				if (att.portId >= udpPortMap) {
					System.out.println("add UDP pipe");
					if (!client.udpMultiplex(loop).add(pipe, port)) {
						System.out.println("failed: dup "+port);
						pipe.close();
					}
				} else {
					synchronized (pipes) {pipes.add(pipe);}

					ClientLaunch c = ClientLaunch.tcp().loop(loop).connect(InetAddress.getLoopbackAddress(), port);
					c.channel()
					 .addLast("proxy", pipe)
					 .addLast("timeout", new TcpTimeout(att));
					c.launch();
				}

				LOGGER.info("开启管道 {}", att);
			} catch (Exception e) {
				LOGGER.error("无法开启管道 {}", e, att);
			}
		}

		@Override
		public void channelClosed(ChannelCtx ctx) {System.out.println("GetPipe Handshake Failed");}
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		super.channelClosed(ctx);
		synchronized (clients) {
			for (Client c : clients.values()) c.close();
			clients.clear();
		}
	}

	private static final class UdpPipe implements ChannelHandler {
		final IntBiMap<Pipe2> proxies = new IntBiMap<>();

		@Override
		public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
			DatagramPkt pkt = (DatagramPkt) msg;
			if (pkt.addr.equals(InetAddress.getLoopbackAddress())) {
				System.out.println("outside packet "+pkt);
				return;
			}

			Pipe2 proxy = proxies.get(pkt.port);
			if (proxy != null && proxy.getRemote().isOpen()) proxy.channelRead(ctx, msg);
		}

		@Override
		public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
			int port = proxies.getInt(Pipe2.CURRENT_WRITER.get());
			assert port != 0;

			ctx.channelWrite(new DatagramPkt(InetAddress.getLoopbackAddress(), port, (DynByteBuf) msg));
		}

		public boolean add(Pipe2 proxy, char port) {
			proxy.setDispatchClose(false);
			proxy.getRemote().addLast("udp_monitor", new ChannelHandler() {
				@Override
				public void channelClosed(ChannelCtx ctx) {
					synchronized (proxies) {proxies.remove(port);}
				}
			});
			synchronized (proxies) {
				if (proxies.containsKey(port)) return false;
				proxies.putInt(port, proxy);
			}
			return true;
		}
	}

	public void init(ClientLaunch ctx, String roomToken, String motd, char[] portMap, int udpOffset) throws IOException {
		MyChannel ch;
		if (AEServer.server != null) {
			EmbeddedChannel[] pair = EmbeddedChannel.createPair();
			AEServer.server.addLocalConnection(pair[0]);
			ch = pair[1];
		} else {
			server = ctx.address();
			ch = ctx.channel();
			prepareLogin(ch);
		}

		handlers = ch;
		this.portMap = portMap;
		this.udpPortMap = udpOffset;

		ByteList b = IOUtil.getSharedByteBuf();
		sendLoginPacket(ch, b.put(PHS_LOGIN).putVUIGB(roomToken).putVUIGB(motd).put(portMap.length*2).putChars(new CharList(portMap)).put(udpOffset));
	}

	final void handleLoginPacket(ChannelCtx ctx, DynByteBuf rb) throws IOException {
		if (rb.readByte() != PHH_LOGON) {
			unknownPacket(ctx, rb);
			return;
		}

		LOGGER.info("logon,{}", rb.hex());

		ctx.channelOpened();
		ctx.removeSelf();
		login = true;
	}

	public void kickSome(int... clientIds) {
		ByteList b = IOUtil.getSharedByteBuf().put(PHS_OPERATION);
		for (int i : clientIds) b.put(0).putInt(i);
		writeAsync(b);
	}

	public static final class Client {
		public final byte[] hash;
		public final String name, addr;
		public final long time = System.currentTimeMillis();

		Client(byte[] hash, String y, String x) { this.hash = hash; name = y; addr = x; }

		@Override
		public String toString() {return "昵称:\""+ Tokenizer.addSlashes(name)+"\" 指纹:"+TextUtil.bytes2hex(hash)+" IP:"+addr;}

		private ServerLaunch udpMpxServer;
		private UdpPipe udpMpx;
		UdpPipe udpMultiplex(SelectorLoop loop) throws IOException {
			if (udpMpx != null) return udpMpx;

			ServerLaunch c = ServerLaunch.udp().loop(loop);
			c.udpCh().addLast("multiplex", udpMpx = new UdpPipe());
			c.launch();

			udpMpxServer = c;
			return udpMpx;
		}

		void close() {IOUtil.closeSilently(udpMpxServer);}

		public boolean checkUdp(char port) {return udpMpx == null || !udpMpx.proxies.containsKey(port);}
	}
}