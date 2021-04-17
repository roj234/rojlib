package roj.net.cross.server;

import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.util.DynByteBuf;

import java.io.IOException;

import static roj.net.cross.Util.*;

/**
 * @author Roj233
 * @since 2021/12/21 13:28
 */
class ClientWork extends Stated {
	static final ClientWork CLIENT_WORK = new ClientWork();

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
				ctx.replaceSelf(Logout.LOGOUT);
				return;
			case PS_REQUEST_CHANNEL:
				int[] tmp = new int[2];

				String refused;
				// group id + pass
				if (rb.readableBytes() != 32 + 1) {
					refused = "参数错误";
				} else {
					refused = W.generatePipe(tmp);
				}

				if (refused != null) {
					print(W + ": 拒绝创建管道: " + refused);
					ctx.channelWrite(IOUtil.getSharedByteBuf().put((byte) P_CHANNEL_OPEN_FAIL).putInt(0).putVUIUTF(refused));
					break;
				}

				DynByteBuf buf = ctx.allocate(false, 33+12);

				buf.put((byte) P_CHANNEL_RESULT).put(rb).putInt(W.clientId).putInt(tmp[0]).putInt(tmp[1]);
				W.room.master.sync(buf);

				ctx.reserve(buf);
				// target is up
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
				rb.rIndex -- ;
				rb.putInt(W.clientId);
				W.room.master.sync(rb);
				break;
			case P_CHANNEL_RESET:
				int pipeId = rb.readInt();
				if (null == W.getPipe(pipeId)) {
					if (DEBUG) {
						print("不存在的管道 " + pipeId);
					}
					ChannelCtx.bite(ctx, (byte) P_FAIL);
					break;
				}

				W.timer = System.currentTimeMillis() + 10000;
				W.waitForReset = pipeId;
				W.room.addPending(pipeId);

				// 1byte head + 4byte pipeId
				rb.rIndex -= 5;
				W.room.master.sync(rb);

				ctx.readInactive();
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
			return;
		}

		if (W.task != null) {
			UPnPPinger task = W.task;
			if (task.state != 0) {
				DynByteBuf tmp = IOUtil.getSharedByteBuf();
				tmp.put((byte) P_UPNP_PING).put((byte) task.state);
				try {
					ctx.channelWrite(tmp);
				} finally {
					W.task = null;
				}
			}
		}

		if (W.waitForReset >= 0) {
			if (System.currentTimeMillis() > W.timer) {
				// 超时了，怎么办
				print("ERROR reset超时！");
				ChannelCtx.bite(ctx, (byte) PS_ERROR_IO);
				W.room.removePending(W.waitForReset);
				ctx.replaceSelf(Logout.LOGOUT);
			} else if (!W.room.isPending(W.waitForReset)) {
				ctx.readActive();
				W.waitForReset = -1;
				ChannelCtx.bite(ctx, (byte) P_CHANNEL_RESET);
			}
		}
	}
}
