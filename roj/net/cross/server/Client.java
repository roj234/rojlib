package roj.net.cross.server;

import roj.collect.IntMap;
import roj.concurrent.PacketBuffer;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.net.ch.*;
import roj.net.ch.handler.MSSCipher;
import roj.net.ch.handler.Timeout;
import roj.net.ch.handler.VarintSplitter;
import roj.net.cross.AEClient;
import roj.util.DynByteBuf;
import roj.util.NamespaceKey;
import roj.util.TypedName;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.util.Iterator;

import static roj.net.cross.Util.PH_CLIENT_LOGOUT;
import static roj.net.cross.Util.print;
import static roj.net.cross.server.AEServer.server;

/**
 * @author Roj233
 * @since 2022/1/24 3:21
 */
public final class Client implements ChannelHandler {
	public static final TypedName<Client> CLIENT = new TypedName<>("CLIENT");

	ChannelCtx handler;
	Room room;
	int clientId;

	// region 统计数据 (可以删除)
	public final long creation;
	public long lastPacket;

	public int getClientId() {
		return clientId;
	}

	public Room getRoom() {
		return room;
	}
	// endregion

	// 状态量
	private Stated state;
	long timer, pingTimer;
	int waitForReset = -1;
	UPnPPinger task;

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		String s = handler.remoteAddress().toString();
		sb.append("[").append(s.startsWith("/") ? s.substring(1) : s).append("]");
		if (room != null) {
			sb.append(" \"").append(room.id).append("\"#").append(clientId);
		}
		return sb.append(": ").toString();
	}

	Client() {
		this.creation = System.currentTimeMillis() / 1000;
	}

	public CMapping serialize() {
		CMapping json = new CMapping();
		json.put("id", clientId);
		json.put("ip", handler.remoteAddress().toString());
		Stated state = this.state;
		json.put("state", state == null ? "CLOSED" : state.getClass().getSimpleName());
		json.put("time", creation);
		long up = 0, down = 0;
		if (!pipes.isEmpty()) {
			synchronized (pipes) {
				for (PipeGroup pg : pipes.values()) {
					Pipe pr = pg.pairRef;
					if (pr == null) continue;
					up += pr.uploaded;
					down += pr.downloaded;
				}
			}
		}
		json.put("up", clientId == 0 ? down : up);
		json.put("down", clientId == 0 ? up : down);
		json.put("heart", lastPacket / 1000);
		json.put("pipe", pipes.size());
		return json;
	}

	static final int MAX_PACKET = 10020;

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		NamespaceKey id = event.id;
		if (id == Timeout.WRITE_TIMEOUT) {
			print(this + " WRITE_TIMEOUT");
			ctx.close();
		} else if (id == Timeout.READ_TIMEOUT) {
			print(this + " READ_TIMEOUT");
		}
	}

	@Override
	public void channelTick(ChannelCtx ctx) throws IOException {
		if (clientId == 0 && !pipes.isEmpty()) {
			long time = System.currentTimeMillis();
			// 嗯...差不多可以，只要脸不太黑...
			if ((time & 127) == 0) {
				synchronized (pipes) {
					for (Iterator<PipeGroup> itr = pipes.values().iterator(); itr.hasNext(); ) {
						PipeGroup pair = itr.next();
						if (pair.pairRef == null || pair.pairRef.idleTime > AEServer.PIPE_TIMEOUT) {
							itr.remove();
							pair.close(-2);
						}
					}
				}
			}
		}

		if (!packets.isEmpty()) {
			DynByteBuf b = ctx.allocate(true, 2048);
			try {
				ctx.channelWrite(packets.take(b));
			} finally {
				ctx.reserve(b);
			}
		}
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		if (server.shutdown) return;

		if (room != null) {
			if (room.master == this) {
				room.master = null;
				server.rooms.remove(room.id);
				room.close();
				System.err.println("close room");
			} else {
				synchronized (room.clients) {
					room.clients.remove(clientId);
				}
				System.err.println("dispatch client logout");
				Client mw = room.master;
				if (mw != null) {
					DynByteBuf tmp = IOUtil.getSharedByteBuf();
					mw.sync(tmp.put((byte) PH_CLIENT_LOGOUT).putInt(clientId));
				}
			}
		}
		server.remain.getAndIncrement();
		print(this + "连接中止");
	}

	private final PacketBuffer packets = new PacketBuffer(10);

	public void sync(DynByteBuf rb) {
		if (rb.readableBytes() > MAX_PACKET) throw new IllegalArgumentException("Packet too big");
		packets.offer(rb);
	}

	final IntMap<PipeGroup> pipes = new IntMap<>();
	PipeGroup pending;

	String generatePipe(int[] tmp) {
		if (pending != null) return "管道 #" + pending.id + " 处于等待状态";
		if (clientId == 0) return "只有客户端才能请求管道";
		if (server.pipes.size() > 100) return "服务器等待打开的管道过多";
		if (pipes.size() > AEClient.MAX_CHANNEL_COUNT) return "你打开的管道过多";
		server.pipeTimeoutHandler();

		PipeGroup group = pending = new PipeGroup();
		group.downOwner = this;
		group.id = server.pipeId++;
		do {
			group.upPass = server.rnd.nextInt();
		} while (group.upPass == 0);
		do {
			group.downPass = server.rnd.nextInt();
		} while (group.downPass == 0 || group.downPass == group.upPass);
		group.pairRef = new Pipe();
		group.pairRef.att = group;
		group.timeout = System.currentTimeMillis() + 5000;

		tmp[0] = group.id;
		tmp[1] = group.upPass;

		synchronized (pipes) {
			pipes.putInt(group.id, group);
		}
		synchronized (room.master.pipes) {
			room.master.pipes.putInt(group.id, group);
		}
		server.pipes.put(group.id, group);

		return null;
	}

	public void closePipe(int pipeId) {
		PipeGroup group;
		synchronized (pipes) {
			group = pipes.remove(pipeId);
		}
		if (group != null) {
			try {
				group.close(clientId == 0 ? 0 : 1);
			} catch (IOException ignored) {}
		}
	}

	public PipeGroup getPipe(int pipeId) {
		return pipes.get(pipeId);
	}

	UPnPPinger ping(byte[] ip, char port, long sec) {
		if (System.currentTimeMillis() - pingTimer < 10000) {
			return null;
		}
		pingTimer = System.currentTimeMillis();

		try {
			UPnPPinger pinger = task = new UPnPPinger(sec);
			MyChannel ctx = MyChannel.openTCP()
									 .addLast("cipher", new MSSCipher())
									 .addLast("splitter", new VarintSplitter(3))
									 .addLast("timeout", new Timeout(5000, 5000))
									 .addLast("UPNP Handler", pinger);
			ctx.connect(new InetSocketAddress(InetAddress.getByAddress(ip), port));
			server.man.register(ctx, null, SelectionKey.OP_CONNECT);
		} catch (Exception e) {
			e.printStackTrace();
			task = null;
		}

		return task;
	}

	@Override
	public void handlerAdded(ChannelCtx ctx) {
		this.handler = ctx;
	}
}
