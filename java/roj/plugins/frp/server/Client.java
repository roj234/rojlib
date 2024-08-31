package roj.plugins.frp.server;

import roj.config.data.CMap;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.net.Pipe;
import roj.util.DynByteBuf;

import java.io.IOException;

import static roj.plugins.frp.server.AEServer.server;

/**
 * @author Roj233
 * @since 2022/1/24 3:21
 */
final class Client extends Connection {
	final Host room;
	int clientId;

	public int getClientId() { return clientId; }
	public Host getRoom() { return room; }

	Client(Host room) {this.room = room;}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		lastPacket = System.currentTimeMillis();

		DynByteBuf rb = (DynByteBuf) msg;
		switch (rb.readUnsignedByte()) {
			case P___HEARTBEAT: rb.readLong(); break;
			case P___LOGOUT: ctx.replaceSelf(Handshake.Closer); return;
			case P___OBSOLETED_3: break;
			default: unknownPacket(ctx, rb); return;
		}

		sendHeartbeat(ctx);
	}

	@Override
	public synchronized void channelClosed(ChannelCtx ctx) {
		if (server.shutdown) return;

		if (room != null) {
			synchronized (room) { room.clients.remove(clientId); }
			room.writeAsync(IOUtil.getSharedByteBuf().put(PHH_CLIENT_LOGOUT).putInt(clientId));
		}

		for (PipeInfo value : pipes) {
			try {
				value.close(this, "连接中止");
			} catch (IOException ignored) {}
		}

		byte[] sessionId = IOUtil.getSharedByteBuf().put(digest).putInt(clientId).toByteArray();
		server.session.remove(sessionId);

		clientId = -1;
		LOGGER.info("[{}] 连接中止", this);
	}

	public synchronized CMap serialize() {
		CMap json = new CMap();
		json.put("id", clientId);
		json.put("ip", handler.remoteAddress().toString());
		json.put("state", "<not implemented>");
		json.put("time", creation);
		long up = 0, down = 0;
		for (PipeInfo pg : pipes) {
			Pipe pr = pg.pipe;
			if (pr == null) continue;
			up += pr.uploaded;
			down += pr.downloaded;
		}
		json.put("up", clientId == 0 ? down : up);
		json.put("down", clientId == 0 ? up : down);
		json.put("heart", lastPacket / 1000);
		json.put("pipe", pipes.size());
		return json;
	}
}