package roj.plugins.cross;

import roj.config.word.ITokenizer;
import roj.io.IOUtil;
import roj.net.ch.*;
import roj.text.TextUtil;
import roj.text.logging.Level;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.net.InetAddress;
import java.net.StandardSocketOptions;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * @version 2.0.3
 * @since 2023/11/02 9:23
 */
public class AEClient extends IAEClient {
	public char[] portMap;
	public int clientId;
	public String roomMotd;

	protected Listener[] servers;

	final ConcurrentHashMap<Integer, GetPipe> tasks = new ConcurrentHashMap<>();
	final ConcurrentLinkedQueue<GetPipe> asyncRead = new ConcurrentLinkedQueue<>();

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
					servers[i] = new Listener(port, i, loop);
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
			case P___CHAT_DATA:
				System.out.println("");
			break;
			case P___NOTHING:
				int id;
				Pipe pair;
			break;
			case P___CHANNEL_CLOSED:
				id = rb.readInt();
				String msg1 = rb.readVUIGB();

				pair = pipes.remove(id);
				if (pair != null) {
					pair.close();
					LOGGER.info("被关闭了管道 {}: {}", pair.att, msg1);
				}
			break; // PCS_REQUEST_CHANNEL
			case PCC_CHANNEL_ALLOW:
				id = rb.readInt(); // session id
				GetPipe task = tasks.remove(id);
				if (task == null) {
					LOGGER.warn("无效的管道 #{}", id);
					// 没法管，反正服务端也会关闭
					break;
				}

				rb.read(task.key, 32, 32);
				task.pipeId = id = rb.readInt();
				asyncPipeLogin(id, task.key, task);
			break;
			case PCC_CHANNEL_DENY:
				task = tasks.remove(rb.readInt());
				if (task == null) break;

				task.fail("拒绝开启管道: "+rb.readVUIGB());
			break;
			default: unknownPacket(ctx, rb); break;
		}

		if (!LOGGER.getLevel().canLog(Level.DEBUG)) rb.rIndex = rb.wIndex();
	}

	@Override
	public void channelTick(ChannelCtx ctx) throws IOException {
		super.channelTick(ctx);

		GetPipe o = asyncRead.poll();
		if (o != null) {
			int session;
			do {
				session = rnd.nextInt();
			} while (tasks.putIfAbsent(session, o) != null);
			o.sessionId = session;

			ByteList b = IOUtil.getSharedByteBuf();
			b.put(PCS_REQUEST_CHANNEL).putInt(session).put(o.portId).put(o.key, 0, 32);
			ctx.channelWrite(b);
		}
	}

	public void init(ClientLaunch ctx, String nickname, String roomToken) {
		server = ctx.address();
		handlers = ctx.channel();
		prepareLogin(ctx.channel());

		ByteList b = IOUtil.getSharedByteBuf();
		sendLoginPacket(ctx.channel(), b.put(PCS_LOGIN).putVUIGB(roomToken).putVUIGB(nickname));
	}

	void handleLoginPacket(ChannelCtx ctx, DynByteBuf rb) throws IOException {
		if (rb.get() != PCC_LOGON) {
			unknownPacket(ctx, rb);
			return;
		}

		int clientId = rb.readInt();
		byte[] roomUserId = rb.readBytes(rb.readUnsignedByte());
		String roomMotd = rb.readVUIGB();
		int portLen = rb.readUnsignedByte();

		LOGGER.info("房间指纹: {}", TextUtil.bytes2hex(roomUserId));
		if (!roomMotd.isEmpty()) LOGGER.info("房间MOTD: \"{}\"", ITokenizer.addSlashes(roomMotd));
		LOGGER.info("客户端ID: {}", clientId);

		if (portLen > MAX_PORTS) throw new IllegalArgumentException("系统限制: 端口映射数量 < 32");
		char[] ports = new char[portLen];
		for (int i = 0; i < portLen; i++) ports[i] = rb.readChar();

		byte[] directAddr = rb.readBoolean() ? rb.readBytes(rb.readableBytes()) : null;

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
				if (cn != null) {
					try {
						cn.socket.close();
					} catch (IOException ignored) {}
				}
			}
		}
		super.channelClosed(ctx);
	}

	final class Listener implements Consumer<MyChannel> {
		final ServerLaunch socket;
		final int portId;
		final char port;

		public Listener(char port, int portId, SelectorLoop loop) throws IOException {
			this.portId = portId;
			this.port = port;

			socket = ServerLaunch
				.tcp().bind2(InetAddress.getLoopbackAddress(), port, 100)
				.option(StandardSocketOptions.SO_REUSEADDR, true)
				.loop(loop).initializator(this).launch();
		}

		@Override
		public void accept(MyChannel ch) {
			try {
				initSocketPref(ch);

				if (pipes.size() > CLIENT_MAX_PIPES) {
					failed(ch, "打开的管道过多("+CLIENT_MAX_PIPES+")");
					return;
				}

				GetPipe gp = new GetPipe(portId, ch);
				rnd.nextBytes(gp.key);
				asyncRead.add(gp);

				ch.readInactive();
				ch.addLast("_null_", gp);
			} catch (IOException e) {
				Helpers.athrow(e);
			}
		}

		void failed(MyChannel ch, String error) throws IOException {
			ch.fireChannelWrite(IOUtil.getSharedByteBuf().putAscii("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n").putUTFData(error));
			ch.close();
			LOGGER.error("新连接创建失败: {}", error);
		}
	}

	final class GetPipe implements Consumer<Pipe>, ChannelHandler {
		final byte[] key = new byte[64];
		final MyChannel income;

		int portId, sessionId, pipeId;

		public GetPipe(int portId, MyChannel income) {
			this.portId = portId;
			this.income = income;
		}

		long times = 1000;
		@Override
		public void channelTick(ChannelCtx ctx) throws Exception {
			if (--times == 0) fail("等待超时(1000)");
		}

		@Override
		public void accept(Pipe pipe) {
			income.readActive();

			pipes.putInt(pipeId, pipe);
			pipe.att = new PipeInfoClient(clientId, pipeId, portId);

			try {
				pipe.setDown(income);
				loop.register(pipe, null);
			} catch (IOException e) {
				e.printStackTrace();
			}

			LOGGER.info("开启管道 {}", pipe.att);
		}
		public void fail(String s) throws IOException {
			tasks.remove(sessionId);
			servers[portId].failed(income, s);
		}
	}

	// region html
	// endregion
}