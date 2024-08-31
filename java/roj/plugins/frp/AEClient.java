package roj.plugins.frp;

import roj.config.Tokenizer;
import roj.io.IOUtil;
import roj.net.*;
import roj.net.handler.MSSCipher;
import roj.net.handler.Pipe2;
import roj.net.handler.Timeout;
import roj.net.handler.VarintSplitter;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.net.InetAddress;
import java.net.StandardSocketOptions;
import java.util.function.Consumer;

/**
 * @since 2023/11/02 9:23
 */
public class AEClient extends IAEClient {
	public int clientId;
	public String roomMotd;

	Listener[] servers;

	public AEClient(SelectorLoop loop) {super(loop);}

	public final int portMapChanged() {
		for (int i = 0; i < servers.length; i++) {
			Listener s = servers[i];
			char port = portMap[i];
			if (s != null) {
				if (port != s.port) {
					try {
						s.socket.close();
					} catch (IOException ignored) {}
					servers[i] = null;
				} else {
					continue;
				}
			}

			if (port > 0) {
				try {
					servers[i] = new Listener(port, i, loop, i >= udpPortMap);
				} catch (IOException e) {
					return i;
				}
			}
		}

		return -1;
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		onlyOneMissed = true;

		DynByteBuf rb = (DynByteBuf) msg;
		switch (rb.readUnsignedByte()) {
			case P___HEARTBEAT: rb.readLong(); break;
			case P___LOGOUT: ctx.close(); break;
			case P___OBSOLETED_3: break;
			default: unknownPacket(ctx, rb); break;
		}
	}

	public void init(ClientLaunch ctx, String nickname, String room) {
		server = ctx.address();
		handlers = ctx.channel();
		prepareLogin(ctx.channel());

		ByteList b = IOUtil.getSharedByteBuf();
		sendLoginPacket(ctx.channel(), b.put(PCS_LOGIN).putVUIGB(room).putVUIGB(nickname));
	}

	void handleLoginPacket(ChannelCtx ctx, DynByteBuf rb) throws IOException {
		if (rb.readByte() != PCC_LOGON) {
			unknownPacket(ctx, rb);
			return;
		}

		int clientId = rb.readInt();
		byte[] roomUserId = rb.readBytes(DIGEST_LENGTH);
		String roomMotd = rb.readVUIGB();
		int portLen = rb.readUnsignedByte()/2;

		LOGGER.info("房间指纹: {}", TextUtil.bytes2hex(roomUserId));
		if (!roomMotd.isEmpty()) LOGGER.info("房间MOTD: \"{}\"", Tokenizer.addSlashes(roomMotd));
		LOGGER.info("客户端ID: {}", clientId);

		char[] ports = new char[portLen];
		for (int i = 0; i < portLen; i++) ports[i] = rb.readChar();

		udpPortMap = rb.readUnsignedByte();

		this.portMap = ports;
		this.clientId = clientId;
		this.roomMotd = roomMotd;
		this.servers = new Listener[ports.length];

		ctx.channelOpened();
		ctx.removeSelf();
		login = true;
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		if (servers != null) {
			for (Listener cn : servers) {
				if (cn != null) IOUtil.closeSilently(cn.socket);
			}
		}

		super.channelClosed(ctx);
	}

	final class Listener implements Consumer<MyChannel>, ChannelHandler {
		final ServerLaunch socket;
		final char port;
		private final int portId;

		public Listener(char port, int portId, SelectorLoop loop, boolean udp) throws IOException {
			this.portId = portId;
			this.port = port;

			socket = (udp ? ServerLaunch.udp() : ServerLaunch.tcp().option(StandardSocketOptions.SO_REUSEADDR, true))
				.bind2(InetAddress.getLoopbackAddress(), port, 100).loop(loop);
			if (udp) socket.udpCh().addLast("udpListener", this);
			else socket.initializator(this);
			socket.launch();
		}

		// only for UDP
		private DatagramPkt myPkt;
		@Override
		public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
			DatagramPkt pkt = (DatagramPkt) msg;
			if (myPkt == null) {
				myPkt = new DatagramPkt(pkt.addr, pkt.port, new ByteList().put(pkt.buf));
				pkt.buf.clear();

				getPipe(ctx.channel(), () -> {
					try {
						ctx.channelRead(myPkt.buf);
					} catch (IOException e) {
						Helpers.athrow(e);
					}
					myPkt = null;
				});
				return;
			}

			ctx.channelRead(pkt.buf);
		}

		@Override
		public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
			// TODO determinate via SeqNum or
			ctx.channelWrite(new DatagramPkt(myPkt.addr, myPkt.port, (DynByteBuf) msg));
		}

		@Override
		public void accept(MyChannel ch) {
			try {
				initSocketPref(ch);

				if (pipes.size() > CLIENT_MAX_PIPES) {
					failed(ch, "打开的管道过多("+CLIENT_MAX_PIPES+")");
					return;
				}

				getPipe(ch, null);
			} catch (IOException e) {
				Helpers.athrow(e);
			}
		}

		private void getPipe(MyChannel ch, Runnable callback) throws IOException {
			ch.readInactive();

			ClientLaunch bs = ClientLaunch.tcp().loop(loop).connect(server);
			bs.channel()
			  .addLast("cipher", new MSSCipher(client_factory.get()))
			  .addLast("splitter", VarintSplitter.twoMbVLUI())
			  .addLast("timeout", new Timeout(2000))
			  .addLast("handshake", new ChannelHandler() {
				  private byte stage;

				  @Override
				  public void channelOpened(ChannelCtx ctx) throws IOException {
					  if (stage == 0) {
						  ctx.channelWrite(IOUtil.getSharedByteBuf().put(PPS_PIPE_CLIENT).putInt(clientId).put(portId));
						  ctx.flush();
						  ctx.channel().handler("splitter").removeSelf();
						  stage = 1;
					  }
				  }

				  @Override
				  public void onEvent(ChannelCtx ctx, Event event) throws IOException {
					  failed(ch, "Pipe Timeout("+CLIENT_TIMEOUT+")");
				  }

				  @Override
				  public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
					  DynByteBuf rb = (DynByteBuf) msg;
					  if (rb.readUnsignedByte() == PPP_LOGON) {
						  if (stage == 1) {
							  ChannelCtx ctx1 = ctx.channel().handler("cipher");
							  ctx1.replaceSelf(new MSSCipher(client_factory.get()));
							  ctx1.handler().channelOpened(ctx1);

							  stage = 2;
							  return;
						  } else {
							  ctx.removeSelf();

							  if (rb.readByte() == portId) {
								  Pipe2 pipe = new Pipe2(ch, true);
								  pipe.att = new PipeInfoClient(clientId, 0, portId);

								  synchronized (pipes) {pipes.add(pipe);}

								  ctx.replaceSelf(pipe);
								  if (callback != null) callback.run();

								  ch.readActive();

								  LOGGER.info("开启管道 {}", pipe.att);
								  return;
							  }
						  }
					  }

					  failed(ch, "GetPipe Handshake Failed: "+rb.dump());
					  ch.close();
					  ctx.close();
				  }
			  });
			bs.launch();
		}

		void failed(MyChannel ch, String error) throws IOException {
			if (socket.isTCP()) {
				ch.fireChannelWrite(IOUtil.getSharedByteBuf().putAscii("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n").putUTFData(error));
				ch.close();
			}
			LOGGER.error("新连接创建失败: {}", error);
		}
	}
	// region html
	// endregion
}