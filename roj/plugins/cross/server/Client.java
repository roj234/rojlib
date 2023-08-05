package roj.plugins.cross.server;

import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.Pipe;
import roj.text.logging.Level;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;

import static roj.plugins.cross.server.AEServer.server;

/**
 * @author Roj233
 * @since 2022/1/24 3:21
 */
final class Client extends Connection implements ChannelHandler {
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
			case P_S_PING:
				byte[] ip = rb.readBytes(rb.readUnsignedByte());
				char port = rb.readChar();

				Pinger task = ping(ip, port);
				if (task == null) {
					rb = IOUtil.getSharedByteBuf();
					ctx.channelWrite(rb.put(P_S_PING).put(-3));
				}
			break;
			case P_S_PONG: pong(rb); break;
			case PCS_REQUEST_CHANNEL:
				id = rb.readInt();
				Object pipe = generatePipe(id);
				ByteList b = IOUtil.getSharedByteBuf();

				if (pipe.getClass() == String.class) {
					LOGGER.info("[{}] 无法创建管道: {}", this, pipe);
					ctx.channelWrite(b.put(PCC_CHANNEL_DENY).putInt(id).putVUIUTF(pipe.toString()));
					break;
				}

				room.writeAsync(b.put(PHH_CLIENT_REQUEST_CHANNEL).put(rb).putInt(clientId).putInt((int) pipe));
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

	Object generatePipe(int sessionId) {
		if (server.pipes.size() > SERVER_MAX_PIPES) return "服务器合计等待打开的管道过多";
		if (pipes.size() > CLIENT_MAX_PIPES) return "你打开的管道过多";
		if (room.pipes.size() > HOST_MAX_PIPES) return "Host打开的管道过多";

		PipeInfo group = new PipeInfo();
		group.pipe = new Pipe();
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
