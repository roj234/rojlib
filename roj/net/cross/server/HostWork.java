package roj.net.cross.server;

import roj.net.ch.ChannelCtx;
import roj.net.cross.Util;
import roj.util.DynByteBuf;

import java.io.IOException;

import static roj.net.cross.Util.*;

/**
 * @author Roj233
 * @since 2021/12/21 13:28
 */
final class HostWork extends Stated {
	static final HostWork HOST_WORK = new HostWork();

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		Client W = ctx.attachment(Client.CLIENT);
		if (!isInRoom(W)) {
			ctx.replaceSelf(Logout.LOGOUT);
			return;
		}

		DynByteBuf rb = (DynByteBuf) msg;
		switch (rb.get() & 0xFF) {
			case P_HEARTBEAT:
				break;
			case P_LOGOUT:
				ctx.replaceSelf(Logout.REQUESTED);
				return;
			case PS_KICK_CLIENT:
				int clientId = rb.readInt();
				Client w = W.room.clients.remove(clientId);
				if (w == null) if (Util.DEBUG) print(W + "踢出客户端: 无效的ID " + clientId);
				break;
			case PS_CHANNEL_OPEN:
				clientId = rb.readInt();
				w = W.room.clients.get(clientId);
				if (w != null && w.pending != null) {
					byte[] rnd2 = new byte[32];
					rb.read(rnd2);
					rb.clear();
					PipeGroup pending = w.pending;
					rb.put((byte) P_CHANNEL_RESULT).put(rnd2).putLong(((long) pending.id << 32) | (pending.downPass & 0xFFFF_FFFFL));
					w.sync(rb);
				} else if (Util.DEBUG) print(W + "管道开启: 无效的ID " + clientId);

				break;
			case P_CHANNEL_OPEN_FAIL:
				clientId = rb.readInt();
				w = W.room.clients.get(clientId);
				if (w != null) {
					rb.clear();
					rb.put((byte) P_CHANNEL_OPEN_FAIL).putInt(W.clientId).putVarIntUTF("E31");
					w.sync(rb);
				} else if (Util.DEBUG) print(W + ": 管道失败: 无效的ID " + clientId);
				break;
			case P_CHANNEL_CLOSE:
				W.closePipe(rb.readInt());
				break;
			case P_EMBEDDED_DATA:
				System.out.println("P_EMBEDDED_DATA暂无用途");
				break;
			case P_UPNP_PING:
				long sec = rb.readLong();
				char port = rb.readChar();
				byte[] ip = new byte[rb.get() & 0xFF];
				rb.read(ip);

				UPnPPinger task;
				if (W.task != null || (task = W.ping(ip, port, sec)) == null) {
					rb.clear();
					rb.put((byte) P_UPNP_PING).put((byte) -3);
					ctx.channelWrite(rb);
				} else {
					W.task = task;
				}
				break;
			case P_UPNP_PONG:
				int prev = rb.rIndex-1;
				W.room.upnpAddress = rb.readBytes(rb.readableBytes());
				// 同步到客户端
				synchronized (W.room.clients) {
					for (Client wk : W.room.clients.values()) {
						rb.rIndex = prev;
						if (wk != W) wk.sync(rb);
					}
				}
				break;
			case P_CHANNEL_RESET:
				if (Util.DEBUG) print(W + ": Signal #" + rb.readInt(1));
				W.room.removePending(rb.readInt(1));
				break;
			default:
				unknownPacket(W, rb);
				ctx.replaceSelf(Logout.LOGOUT);
				return;
		}

		ChannelCtx.bite(ctx, (byte) P_HEARTBEAT);
	}

	@Override
	public void channelTick(ChannelCtx ctx) throws IOException {
		Client W = ctx.attachment(Client.CLIENT);

		if (!isInRoom(W)) {
			ctx.replaceSelf(Logout.LOGOUT);
		}
	}
}
