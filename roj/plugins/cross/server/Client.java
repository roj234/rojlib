package roj.plugins.cross.server;

import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.Pipe;
import roj.text.logging.Level;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetSocketAddress;

import static roj.plugins.cross.server.AEServer.server;

/**
 * @author Roj233
 * @since 2022/1/24 3:21
 */
final class Client extends Connection {
	Host room;
	int clientId;

	public int getClientId() { return clientId; }
	public Host getRoom() { return room; }

	Client() {}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		lastPacket = System.currentTimeMillis();

		DynByteBuf rb = (DynByteBuf) msg;
		switch (rb.readUnsignedByte()) {
			case P___HEARTBEAT: break;
			case P___LOGOUT: ctx.channel().close(); return;
			case P___CHAT_DATA:
				System.out.println("P_CHAT_DATA暂无用途");
			break;
			case P___NOTHING:
				int id;
				PipeInfo pi;
			break;
			case P_S_CONNECT_REQ:
				int target_user = rb.readInt();
				Client client = room.clients.get(target_user);
				rb = IOUtil.getSharedByteBuf();
				if (client == null) {
					ctx.channelWrite(rb.put(P_S_CONNECT_ACK).putBool(false).putVUIGB("client not exist"));
					break;
				}

				InetSocketAddress addr = (InetSocketAddress) ctx.channel().remoteAddress();
				byte[] bytes = addr.getAddress().getAddress();
				client.writeAsync(rb.put(P_S_CONNECT_REQ).putInt(clientId).put(bytes.length).put(bytes).putShort(addr.getPort()));
			break;
			case P_S_CONNECT_ACK: pong(rb); break;
			case PCS_REQUEST_CHANNEL:
				id = rb.readInt();
				boolean relay = rb.readBoolean();
				Object pipe = makePipe(id, relay);
				ByteList b = IOUtil.getSharedByteBuf();

				if (pipe.getClass() == String.class) {
					LOGGER.info("[{}] 无法创建管道: {}", this, pipe);
					ctx.channelWrite(b.put(PCC_CHANNEL_DENY).putInt(id).putVUIUTF(pipe.toString()));
					break;
				}

				room.writeAsync(b.put(PHH_CLIENT_REQUEST_CHANNEL).put(rb).putInt(clientId).putBool(relay).putInt((int) pipe));
				rb.rIndex += 33;
			break;
			default: unknownPacket(ctx, rb); return;
		}

		if (!LOGGER.getLevel().canLog(Level.DEBUG)) rb.rIndex = rb.wIndex();
		sendHeartbeat(ctx);
	}

	@Override
	public void channelClosed(ChannelCtx ctx) {
		if (server.shutdown) return;

		if (room != null) {
			synchronized (room.clients) { room.clients.remove(clientId); }

			DynByteBuf b = IOUtil.getSharedByteBuf();
			room.writeAsync(b.put(PHH_CLIENT_LOGOUT).putInt(clientId));
		}

		LOGGER.info("[{}] 连接中止", this);
	}

	private Object makePipe(int sessionId, boolean relay) {
		if (server.pipes.size() > SERVER_MAX_PIPES) return "服务器合计等待打开的管道过多";
		if (pipes.size() > CLIENT_MAX_PIPES) return "你打开的管道过多";
		if (room.pipes.size() > HOST_MAX_PIPES) return "Host打开的管道过多";

		PipeInfo group = new PipeInfo();
		group.pipe = relay ? new Pipe() : null;
		group.sessionId = sessionId;
		group.timeout = System.currentTimeMillis() + CLIENT_TIMEOUT;

		int a, b;
		do {
			a = server.rnd.nextInt();
		} while (server.pipes.putIfAbsent(a, group) != null);
		group.hostId = a;
		group.host = room;

		do {
			b = server.rnd.nextInt();
		} while (a == b || server.pipes.putIfAbsent(b, group) != null);
		group.clientId = b;
		group.client = this;

		// 仅供统计和reset用
		synchronized (pipes) { pipes.putInt(group.clientId, group); }
		synchronized (room.pipes) { room.pipes.putInt(group.hostId, group); }

		return group.hostId;
	}

	public CMapping serialize() {
		CMapping json = new CMapping();
		json.put("id", clientId);
		json.put("ip", handler.remoteAddress().toString());
		json.put("state", "<not implemented>");
		json.put("time", creation);
		long up = 0, down = 0;
		if (!pipes.isEmpty()) {
			synchronized (pipes) {
				for (PipeInfo pg : pipes.values()) {
					Pipe pr = pg.pipe;
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
}