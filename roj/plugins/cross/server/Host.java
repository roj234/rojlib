package roj.plugins.cross.server;

import roj.collect.IntMap;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.Pipe;
import roj.text.logging.Level;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.Iterator;

import static roj.plugins.cross.server.AEServer.server;

/**
 * @author Roj233
 * @since 2022/1/24 3:21
 */
final class Host extends Connection implements ChannelHandler {
	String token;

	String motdString;
	byte[] motd, portMap;

	final IntMap<Client> clients = new IntMap<>();
	int index;

	// 配置项
	public boolean locked;

	public Host(String token) {
		this.token = token;
		this.index = 1;
	}

	public void kick(int id) {
		Client c;
		synchronized (clients) { c = clients.remove(id); }
		if (c != null) c.kickWithMessage(PS_ERROR_KICKED);
	}
	public void kickAll(int message) {
		synchronized (clients) {
			for (Client w : clients.values()) {
				w.kickWithMessage(message);
			}
			clients.clear();
		}
	}

	public int getClientId() { return 0; }
	public Host getRoom() { return this; }

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		lastPacket = System.currentTimeMillis();

		DynByteBuf rb = (DynByteBuf) msg;
		switch (rb.readUnsignedByte()) {
			case P___HEARTBEAT: break;
			case P___LOGOUT: ctx.channel().close(); return;
			case P___CHAT_DATA:
				System.out.println("P_EMBEDDED_DATA暂无用途");
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
					rb.put(P_S_PING).put(-3);
					ctx.channelWrite(rb);
				}
			break;
			case P_S_PONG: pong(rb); break;
			case PHS_CHANNEL_ALLOW:
				id = rb.readInt();
				PipeInfo pipe = pipes.get(id);
				if (pipe != null) {
					ByteList b = IOUtil.getSharedByteBuf();
					b.put(PCC_CHANNEL_ALLOW).putInt(pipe.sessionId).put(rb, 32).putInt(pipe.clientId);
					rb.rIndex += 32;

					Client c = pipe.client;
					if (c != null) c.writeAsync(b);
				} else {
					LOGGER.debug("[{}] P_HOST_CHANNEL_x ref invalid id {}", this, id);
				}
			break;
			case PHS_CHANNEL_DENY:
				id = rb.readInt();
				pipe = pipes.get(id);
				if (pipe != null) {
					ByteList b = IOUtil.getSharedByteBuf();
					int len = rb.readVUInt();
					b.put(PCC_CHANNEL_DENY).putInt(pipe.sessionId).putVUInt(len).put(rb, len);
					rb.rIndex += len;

					Client c = pipe.client;
					if (c != null) c.writeAsync(b);
				} else {
					LOGGER.debug("[{}] P_HOST_CHANNEL_x ref invalid id {}", this, id);
				}
			break;
			case PHS_OPERATION:
				while (rb.isReadable()) {
					int op = rb.readUnsignedByte();
					id = rb.readInt();
					switch (op) {
						case 0: // KICK
							kick(id);
						break;
						case 1: // todo BAN
						break;
						case 2: // UNBAN
						break;
					}
				}
			break;
			case PHS_UPDATE_MOTD:
				int len = rb.readableBytes();
				if (len > MAX_MOTD) len = MAX_MOTD;
				motd = rb.readBytes(len);
				motdString = new ByteList(motd).readGB(motd.length);
			break;
			default: unknownPacket(ctx, rb); return;
		}

		if (!LOGGER.getLevel().canLog(Level.DEBUG)) rb.rIndex = rb.wIndex();
		sendHeartbeat(ctx);
	}

	@Override
	public void channelTick(ChannelCtx ctx) throws IOException {
		if (!pipes.isEmpty()) {
			long time = System.currentTimeMillis();
			// 嗯...差不多可以，只要脸不太黑...
			if ((time & 127) == 0) {
				synchronized (pipes) {
					for (Iterator<PipeInfo> itr = pipes.values().iterator(); itr.hasNext(); ) {
						PipeInfo info = itr.next();
						if (info.connected != 7) continue;

						Pipe pipe = info.pipe;
						if (pipe.isUpstreamEof() || pipe.isDownstreamEof() || pipe.idleTime > PIPE_IDLE_MAX) {
							itr.remove();
							info.close(this, pipe.isUpstreamEof()?"isUpstreamEof":pipe.isDownstreamEof()?"isDownstreamEof":"idle");
						}
					}
				}
			}
		}

		super.channelTick(ctx);
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		if (server.shutdown) return;

		server.rooms.remove(token);
		kickAll(PS_ERROR_ROOM_CLOSE);

		LOGGER.info("[{}] 连接中止", this);
	}

	public CMapping serialize() {
		long up = 0, down = 0;
		if (!pipes.isEmpty()) {
			synchronized (pipes) {
				for (PipeInfo group : pipes.values()) {
					Pipe ref = group.pipe;
					if (ref != null) {
						up += ref.downloaded;
						down += ref.uploaded;
					}
				}
			}
		}

		CMapping json = new CMapping();
		json.put("id", token);
		json.put("ip", handler.remoteAddress().toString());
		json.put("time", creation);
		json.put("up", up);
		json.put("down", down);
		json.put("users", clients.size());
		json.put("index", index);
		json.put("motd", motdString);
		json.put("master", handler.remoteAddress().toString());
		json.put("heart", lastPacket / 1000);
		json.put("pipe", pipes.size());
		return json;
	}
}
