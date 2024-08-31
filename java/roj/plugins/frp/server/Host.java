package roj.plugins.frp.server;

import roj.collect.Hasher;
import roj.collect.IntMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.concurrent.timing.ScheduleTask;
import roj.concurrent.timing.Scheduler;
import roj.config.data.CMap;
import roj.net.ChannelCtx;
import roj.net.Pipe;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.Iterator;

import static roj.plugins.frp.server.AEServer.server;

/**
 * @author Roj233
 * @since 2022/1/24 3:21
 */
final class Host extends Connection {
	String name;

	String motdString;
	byte[] motd, portMap;
	int udpOffset;

	final IntMap<Client> clients = new IntMap<>();
	int nextId;

	final MyHashSet<byte[]> blacklist = new MyHashSet<>(Hasher.array(byte[].class));
	boolean whitelist;

	public Host(String name) {this.name = name;}

	public synchronized void kick(int id) {
		Client c = clients.remove(id);
		if (c != null) c.kickWithMessage(PS_ERROR_KICKED);
	}
	public void kickAll(int message) {
		SimpleList<Client> list;
		synchronized (this) {
			list = new SimpleList<>(clients.values());
			clients.clear();
		}

		for (Client w : list) w.kickWithMessage(message);
	}

	public int getClientId() { return portMap == null ? -1 : 0; }
	public Host getRoom() { return this; }

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		lastPacket = System.currentTimeMillis();

		DynByteBuf rb = (DynByteBuf) msg;
		switch (rb.readUnsignedByte()) {
			case P___HEARTBEAT: rb.readLong(); break;
			case P___LOGOUT: ctx.replaceSelf(Handshake.Closer); return;
			case P___OBSOLETED_3: int id; break;
			case PHS_CHANNEL_DENY:
				DynByteBuf sessionId = rb.slice(16);
				PipeInfo info = server.pipes.remove(sessionId);
				if (info != null) {
					info.close(this, rb.readVUIGB());
					// will remove from Host.pipes
				} else {
					rb.rIndex = rb.wIndex();
					LOGGER.debug("[{}] P_HOST_CHANNEL_x ref invalid id {}", this, sessionId.hex());
				}
			break;
			case PHS_OPERATION:
				while (rb.isReadable()) {
					switch (rb.readUnsignedByte()) {
						case 0 -> kick(rb.readInt());
						case 1 -> blacklist.add(rb.readBytes(DIGEST_LENGTH));
						case 2 -> blacklist.remove(rb.readBytes(DIGEST_LENGTH));
						case 3 -> whitelist = rb.readBoolean();
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

		sendHeartbeat(ctx);
	}

	private ScheduleTask ticker;

	@Override
	public void handlerAdded(ChannelCtx ctx) {
		super.handlerAdded(ctx);
		if (ticker == null) ticker = Scheduler.getDefaultScheduler().loop(() -> {
			if (pipes.isEmpty()) return;
			synchronized (this) {
				for (Iterator<PipeInfo> itr = pipes.iterator(); itr.hasNext(); ) {
					PipeInfo info = itr.next();
					if (info.isClosed()) {
						itr.remove();
						info.close(this, "idle");
					}
				}
			}
		}, 200);
	}

	@Override
	public synchronized void channelClosed(ChannelCtx ctx) {
		if (ticker != null) ticker.cancel();

		if (server.shutdown) return;

		portMap = null;
		server.rooms.remove(name);
		kickAll(PS_ERROR_ROOM_CLOSE);

		LOGGER.info("[{}] 连接中止", this);
	}

	public synchronized CMap serialize() {
		long up = 0, down = 0;
		for (PipeInfo group : pipes) {
			Pipe ref = group.pipe;
			if (ref != null) {
				up += ref.downloaded;
				down += ref.uploaded;
			}
		}

		CMap json = new CMap();
		json.put("id", name);
		json.put("ip", handler.remoteAddress().toString());
		json.put("time", creation);
		json.put("up", up);
		json.put("down", down);
		json.put("users", clients.size());
		json.put("motd", motdString);
		json.put("master", handler.remoteAddress().toString());
		json.put("heart", lastPacket / 1000);
		json.put("pipe", pipes.size());
		return json;
	}
}