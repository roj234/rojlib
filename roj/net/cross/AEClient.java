package roj.net.cross;

import roj.io.IOUtil;
import roj.net.ch.*;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import static roj.net.cross.Util.*;

/**
 * AbyssalEye Client
 *
 * @author Roj233
 * @since 2021/8/18 0:09
 */
public class AEClient extends IAEClient {
	public static final int MAX_CHANNEL_COUNT = 6;

	public char[] portMap;
	public int clientId;

	protected Listener[] servers;

	final ConcurrentLinkedQueue<Object> asyncTick, asyncRead;

	public AEClient(SocketAddress server, String id, String token) {
		super(server, id, token);
		this.asyncTick = new ConcurrentLinkedQueue<>();
		this.asyncRead = new ConcurrentLinkedQueue<>();
	}

	public final void awaitLogin() throws InterruptedException {
		if (free != null) return;
		synchronized (this) {
			wait();
		}
	}

	protected void notifyLogon() {
		synchronized (this) {
			notifyAll();
		}
	}

	public final void notifyPortMapModified() throws IOException {
		if (free == null) throw new IOException("Client closed");

		for (int j = 0; j < servers.length; j++) {
			Listener cn = servers[j];
			char port = portMap[j];
			if (cn != null) {
				if (port != cn.port) {
					try {
						cn.socket.close();
					} catch (IOException ignored) {}
					servers[j] = null;
				}
			}
			if (servers[j] == null && port > 0) {
				servers[j] = new Listener(port, j);
				try {
					servers[j].start(loop);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		received = true;

		DynByteBuf rb = (DynByteBuf) msg;
		switch (rb.get() & 0xFF) {
			case P_FAIL:
				print("上次的操作失败了");
				break;
			case P_HEARTBEAT:
				break;
			case P_LOGOUT:
				logout(ctx);
				break;
			case P_CHANNEL_CLOSE:
				int src = rb.readInt();
				int id = rb.readInt();

				Pipe pair = socketsById.remove(id);
				if (pair == null) break;

				pair.close();

				List<Pipe> pairs = free[((SpAttach) pair.att).portId];
				if (!pairs.isEmpty()) pairs.remove(pair);

				print((src < 0 ? "服务端" : "房主") + "关闭了频道 #" + id);
				break;
			case P_CHANNEL_OPEN_FAIL:
				GetPipe task = (GetPipe) asyncRead.poll();
				if (task == null || task.cipher == null) throw new IOException("错误的状态");

				src = rb.readInt();
				task.fail((src < 0 ? "服务端" : "房主") + "拒绝开启频道: " + rb.readVUIUTF());
				break;
			case P_CHANNEL_RESULT:
				task = (GetPipe) asyncRead.poll();
				if (task == null || task.cipher == null) throw new IOException("错误的状态");

				rb.read(task.cipher, 32, 32);
				long pipe = rb.readLong();
				print("申请了频道 #" + (pipe >>> 32));
				asyncPipeLogin(pipe, task.cipher, task);
				break;
			case P_CHANNEL_RESET:
				print("服务端返回重置完毕");
				break;
			case P_EMBEDDED_DATA:
				break;
			default:
				onError(rb, null);
				ctx.close();
				break;
		}
	}

	@Override
	public void channelTick(ChannelCtx ctx) throws IOException {
		if (portMap == null) return;

		super.channelTick(ctx);

		ByteList tmp = IOUtil.getSharedByteBuf();
		Object o = asyncTick.poll();
		checkInterrupt:
		if (o instanceof Pipe) {
			// release
			Pipe pipe = (Pipe) o;
			SpAttach att = (SpAttach) pipe.att;
			List<Pipe> pairs = free[att.portId];
			if (pairs == Collections.EMPTY_LIST) pairs = free[att.portId] = new ArrayList<>(3);
			pairs.add(pipe);
		} else if (o instanceof GetPipe) {
			GetPipe gp = (GetPipe) o;
			if (socketsById.size() > MAX_CHANNEL_COUNT) {
				gp.fail("打开的频道过多(" + MAX_CHANNEL_COUNT + ")");
				break checkInterrupt;
			}

			asyncRead.offer(gp);

			List<Pipe> pairs = free[gp.portId];
			if (pairs.isEmpty()) {
				byte[] cipher = gp.cipher = new byte[64];
				rnd.nextBytes(cipher);

				tmp.put((byte) PS_REQUEST_CHANNEL).put((byte) gp.portId).put(cipher, 0, 32);
				break checkInterrupt;
			}

			Pipe target = pairs.remove(pairs.size() - 1);
			SpAttach att = (SpAttach) target.att;

			if (DEBUG) print("复用 #" + att);
			// 通知对面重新连接下
			tmp.put((byte) P_CHANNEL_RESET).putInt(att.channelId);

			gp.accept(target);
		}

		if (tmp.wIndex() > 0) {
			ctx.channelWrite(tmp);
		}
	}

	@Override
	protected void prepareLogin(MyChannel ctx) {
		byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);
		byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);

		ByteList tmp = IOUtil.getSharedByteBuf();
		tmp.put((byte) idBytes.length).put((byte) tokenBytes.length).put(idBytes).put(tokenBytes);

		ctx.addLast("auth", new AEAuthenticator(tmp.toByteArray(), PS_LOGIN_C)).addLast("auth_after", new ChannelHandler() {
			@Override
			public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
				DynByteBuf rb = (DynByteBuf) msg;
				int infoLen = rb.get() & 0xFF;
				int motdLen = rb.get() & 0xFF;
				int portLen = rb.get() & 0xFF;

				int clientId = rb.readInt();

				print("服务器MOTD: " + rb.readUTF(infoLen));
				print("房间MOTD: " + rb.readUTF(motdLen));
				print("客户端ID: " + clientId);

				if (portLen > 32) throw new IllegalArgumentException("系统限制: 端口映射数量 < 32");
				char[] ports = new char[portLen];
				for (int i = 0; i < portLen; i++) {
					ports[i] = rb.readChar();
				}

				ctx.removeSelf();

				AEClient.this.portMap = ports;
				AEClient.this.clientId = clientId;
				AEClient.this.free = Helpers.cast(new List<?>[ports.length]);
				Arrays.fill(free, Collections.emptyList());
				AEClient.this.servers = new Listener[ports.length];

				notifyLogon();
			}
		});
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
		final ServerSock socket;
		final int portId;
		final char port;

		public Listener(char port, int portId) throws IOException {
			socket = ServerSock.openTCP().bind(InetAddress.getLoopbackAddress(), port, 100).setOption(StandardSocketOptions.SO_REUSEADDR, true);
			this.portId = portId;
			this.port = port;
		}

		public void start(SelectorLoop loop) throws IOException {
			socket.register(loop, this);
		}

		@Override
		public void accept(MyChannel ch) {
			try {
				initSocketPref(ch);
				asyncTick.offer(new GetPipe(portId, ch));
			} catch (IOException e) {
				Helpers.athrow(e);
			}
		}
	}

	final class GetPipe implements Consumer<Pipe> {
		final int portId;
		final MyChannel ch;
		byte[] cipher;

		GetPipe(int id, MyChannel ch) {
			portId = id;
			this.ch = ch;
		}

		public void accept(Pipe pair) {
			try {
				pair.setDown(ch);
				loop.register(pair, (Consumer<Pipe>) (pipe) -> {
					if (pipe.isUpstreamEof()) {
						System.out.println("管道结束 " + pipe.att);
						return;
					}
					try {
						asyncTick.offer(pipe);
					} catch (Throwable e) {
						System.out.println("管道回收失败 " + pipe.att);
						e.printStackTrace();
					}
				});
			} catch (Exception e) {
				try {
					ch.close();
				} catch (IOException ignored) {}
			}
		}

		public void fail(String s) {
			System.out.println("GetPipe() failed with " + s);
		}
	}
}
