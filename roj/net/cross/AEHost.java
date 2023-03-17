package roj.net.cross;

import roj.collect.IntMap;
import roj.concurrent.PacketBuffer;
import roj.io.IOUtil;
import roj.net.NetworkUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.MyChannel;
import roj.net.ch.Pipe;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static roj.net.cross.Util.*;

/**
 * AbyssalEye Host
 *
 * @author Roj233
 * @version 0.4.0
 * @since 2021/9/12 0:57
 */
public class AEHost extends IAEClient {
	static final int MAX_CHANNEL_COUNT = 100;

	IntMap<Client> clients;
	char[] portMap;
	public String motd;

	PacketBuffer packets;

	public AEHost(SocketAddress server, String id, String token) {
		super(server, id, token);
		this.clients = new IntMap<>();
		this.portMap = new char[1];
		this.packets = new PacketBuffer(10);
	}

	public void kickSome(int... clientIds) {
		ByteList tmp = ByteList.allocate(clientIds.length * 5);
		for (int i : clientIds) {
			tmp.put((byte) PS_KICK_CLIENT).putInt(i);
		}
		packets.offer(tmp);
	}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		Arrays.fill(free = Helpers.cast(new List<?>[portMap.length]), Collections.emptyList());
		ctx.channelOpened();
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		received = true;

		DynByteBuf rb = (DynByteBuf) msg;
		int ridx = rb.rIndex;
		switch (rb.get() & 0xFF) {
			case P_FAIL:
				print("上次的操作失败了");
				break;
			case P_HEARTBEAT:
				break;
			case P_LOGOUT:
				logout(ctx);
				return;
			case P_CHANNEL_RESET:
				int id = rb.readInt();
				Pipe pair = socketsById.get(id);
				if (pair != null) {
					reset(pair);
				} else {
					print("无效的重置 #" + id);
				}

				rb.rIndex = ridx;
				ctx.channelWrite(rb);
				break;
			case P_CHANNEL_CLOSE:
				int src = rb.readInt();
				id = rb.readInt();
				pair = socketsById.remove(id);
				if (pair != null) {
					pair.close();
					print((src < 0 ? "服务端" : "客户端") + "关闭了频道 #" + Integer.toHexString(id));
				}
				break;
			case P_CHANNEL_RESULT:
				int portId = rb.get() & 0xFF;

				byte[] ciphers = new byte[64];
				rnd.nextBytes(ciphers);
				rb.read(ciphers, 0, 32);

				int clientId = rb.readInt();

				if (socketsById.size() >= MAX_CHANNEL_COUNT || !clients.containsKey(clientId)) {
					String reason = clients.containsKey(clientId) ? "这边开启的频道过多(" + MAX_CHANNEL_COUNT + ")" : "未知的客户端";
					print("客户端 #" + clientId + " 开启频道被阻止: " + reason);

					ByteList tmp = IOUtil.getSharedByteBuf();
					tmp.put((byte) P_CHANNEL_OPEN_FAIL).putInt(clientId).putVarIntUTF(reason);
					ctx.channelWrite(tmp);
				} else {
					asyncPipeLogin(rb.readLong(), ciphers, pipe -> {
						SpAttach att = (SpAttach) pipe.att;
						att.portId = (byte) portId;
						att.clientId = clientId;

						ByteList tmp1 = IOUtil.getSharedByteBuf().put((byte) PS_CHANNEL_OPEN).putInt(att.clientId).put(ciphers, 32, 32);

						try {
							packets.offer(tmp1);
							reset(pipe);
						} catch (IOException e) {
							e.printStackTrace();
						}
					});
				}

				break;
			case PH_CLIENT_LOGIN:
				clientId = rb.readInt();
				char port = rb.readChar();
				byte[] ip = new byte[rb.get() & 0xFF];
				rb.read(ip);
				String address = NetworkUtil.bytes2ip(ip) + ':' + (int) port;
				clients.putInt(clientId, new Client(address));
				print("客户端 #" + clientId + " 上线了, 它来自 " + address);
				break;
			case PH_CLIENT_LOGOUT:
				clientId = rb.readInt();
				if (clients.remove(clientId) != null) {
					print("客户端 #" + clientId + " 下线了.");
				}
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
		super.channelTick(ctx);

		if (!packets.isEmpty()) {
			DynByteBuf b = ctx.allocate(true, 2048);
			try {
				ctx.channelWrite(packets.take(b));
			} finally {
				ctx.reserve(b);
			}
		}
	}

	private void reset(Pipe pipe) throws IOException {
		SpAttach att = (SpAttach) pipe.att;
		if (DEBUG) print(att + " reset.");

		MyChannel c = MyChannel.openTCP();

		Consumer<Object> err = (v) -> {
			try {
				c.close();
			} catch (IOException ignored) {}
			try {
				pipe.close();
			} catch (IOException ignored) {}
			print("管道错误 #" + att.channelId + " of " + att.clientId);
		};

		try {
			initSocketPref(c);
			c.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), portMap[att.portId]), 300);
			c.addLast("connect_handler", new ChannelHandler() {
				@Override
				public void channelOpened(ChannelCtx ctx) throws IOException {
					pipe.setDown(ctx.channel());
					loop.register(pipe, null);
				}
			});

			loop.register(c, Helpers.cast(err));
		} catch (Throwable e) {
			err.accept(null);
			e.printStackTrace();
		}
	}

	@Override
	protected void prepareLogin(MyChannel ctx) {
		byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);
		byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);
		byte[] motdBytes = motd.getBytes(StandardCharsets.UTF_8);

		ByteList tmp = IOUtil.getSharedByteBuf();
		tmp.put((byte) idBytes.length).put((byte) tokenBytes.length).put((byte) motdBytes.length).put((byte) portMap.length).put(idBytes).put(tokenBytes).put(motdBytes);
		for (char c : portMap) {
			tmp.writeChar(c);
		}

		ctx.addLast("auth", new AEAuthenticator(tmp.toByteArray(), PS_LOGIN_H)).addLast("auth_after", new ChannelHandler() {
			@Override
			public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
				DynByteBuf rb = (DynByteBuf) msg;
				print("MOTD: " + rb.readUTF(rb.get() & 0xFF));
				ctx.removeSelf();
			}
		});
	}

	public void setPortMap(char... chars) {
		this.portMap = chars;
	}

	public static final class Client {
		public final String addr;
		public final long connect = System.currentTimeMillis();

		Client(String addr) {this.addr = addr;}
	}
}