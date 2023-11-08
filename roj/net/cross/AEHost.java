package roj.net.cross;

import roj.collect.IntMap;
import roj.config.word.ITokenizer;
import roj.io.IOUtil;
import roj.net.NetworkUtil;
import roj.net.ch.*;
import roj.net.cross.server.AEServer;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.text.logging.Level;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetAddress;

/**
 * @author Roj233
 * @version 2.0.3
 * @since 2023/11/02 9:23
 */
public class AEHost extends IAEClient {
	IntMap<Client> clients = new IntMap<>();
	char[] portMap;

	public AEHost(SelectorLoop loop) {super(loop);}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		onlyOneMissed = true;

		DynByteBuf rb = (DynByteBuf) msg;
		int ridx = rb.rIndex;
		switch (rb.readUnsignedByte()) {
			case P___HEARTBEAT: rb.readLong(); break;
			case P___LOGOUT: ctx.close(); break;
			case P___CHAT_DATA:
				System.out.println("");
			break;
			case P___NOTHING:
				int id;
				Pipe pair;
			break;
			case P___CHANNEL_CLOSED:
				id = rb.readInt();
				pair = pipes.remove(id);
				if (pair != null) {
					pair.close();
					String msg1 = rb.readVUIGB();
					LOGGER.info("被动关闭了频道 #{}: {}", id, msg1);
				}
			break;
			case PHH_CLIENT_REQUEST_CHANNEL:
				int portId = rb.readUnsignedByte();

				byte[] key = new byte[64];
				rnd.nextBytes(key);
				rb.read(key, 0, 32);

				int clientId = rb.readInt();
				int pipeId = rb.readInt();

				ByteList b = new ByteList(); // AEServer.server on local mode also uses IOUtil.getSharedByteBuf()
				if (pipes.size() >= HOST_MAX_PIPES || !clients.containsKey(clientId)) {
					String reason = clients.containsKey(clientId) ? "Host开启的管道过多("+HOST_MAX_PIPES+")" : "未知的客户端";
					LOGGER.info("客户端 #{} 开启管道被阻止: {}", clientId, reason);

					ctx.channelWrite(b.put(PHS_CHANNEL_DENY).putInt(pipeId).putVUIGB(reason));
				} else {
					ctx.channelWrite(b.put(PHS_CHANNEL_ALLOW).putInt(pipeId).put(key, 32, 32));

					PipeInfoClient att = new PipeInfoClient(clientId, pipeId, portId);
					asyncPipeLogin(pipeId, key, pipe -> {
						pipe.att = att;

						LOGGER.info("开启管道 {}", att);

						try {
							ClientLaunch c = ClientLaunch.tcp().loop(loop).timeout(800).connect(InetAddress.getLoopbackAddress(), portMap[att.portId]);
							c.channel().addLast("@@", new ChannelHandler() {
								boolean opened;

								@Override
								public void channelOpened(ChannelCtx ctx) throws IOException {
									opened = true;
									try {
										pipe.setDown(ctx.channel());
										loop.register(pipe, null);
										synchronized (pipes) { pipes.putInt(att.pipeId, pipe); }
									} catch (Throwable e) {
										opened = false;
										throw e;
									}

									LOGGER.debug("连接本地成功 {}", att);
								}

								@Override
								public void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {
									ex.printStackTrace();
									ctx.close();
									channelClosed(ctx);
								}

								@Override
								public void channelClosed(ChannelCtx ctx) throws IOException {
									if (!opened) {
										LOGGER.debug("连接本地失败 {}", att);
										pipe.close();
									}
								}
							});

							c.launch();
						} catch (Exception e) {
							e.printStackTrace();
						}
					});
				}
			break;
			case PHH_CLIENT_LOGIN:
				clientId = rb.readInt();

				byte[] digest = rb.readBytes(rb.readUnsignedByte());
				String name = rb.readVUIGB();
				String ip = NetworkUtil.bytes2ip(rb.readBytes(rb.readableBytes()));

				Client client = new Client(digest, name, ip);
				clients.putInt(clientId, client);

				LOGGER.info("客户端 #{} ({}) 上线了.", clientId, client);
			break;
			case PHH_CLIENT_LOGOUT:
				clientId = rb.readInt();
				client = clients.remove(clientId);
				if (client != null) {
					LOGGER.info("客户端 #{} ({}) 下线了.", clientId, client);
				}
			break;
			default: unknownPacket(ctx, rb); break;
		}

		if (!LOGGER.getLevel().canLog(Level.DEBUG)) rb.rIndex = rb.wIndex();
	}

	public void init(ClientLaunch ctx, String roomToken, String motd, char[] portMap) throws IOException {
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

		ByteList b = IOUtil.getSharedByteBuf();
		sendLoginPacket(ch, b.put(PHS_LOGIN).putVUIGB(roomToken).putVUIGB(motd).put(portMap.length).putChars(new CharList(portMap)));
	}

	final void handleLoginPacket(ChannelCtx ctx, DynByteBuf rb) throws IOException {
		if (rb.get() != PHH_LOGON) {
			unknownPacket(ctx, rb);
			return;
		}

		String roomToken1 = rb.readVUIGB();
		LOGGER.info("roomToken1: {}", roomToken1);

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
		public String toString() {
			return "昵称:\""+ITokenizer.addSlashes(name)+"\" 指纹:"+TextUtil.bytes2hex(hash)+" IP:"+addr;
		}
	}

	// region html
	// endregion
}