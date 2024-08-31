package roj.plugins.frp.server;

import roj.collect.MyHashSet;
import roj.config.data.CMap;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.net.Event;
import roj.net.handler.Timeout;
import roj.plugins.frp.Constants;
import roj.text.CharList;
import roj.util.ByteList;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2023/11/2 0002 5:31
 */
public abstract class Connection extends Constants {
	ChannelCtx handler;
	byte[] digest;

	// 统计数据
	public final long creation;
	public long lastPacket;

	Connection() { creation = System.currentTimeMillis(); }

	public abstract int getClientId();
	public abstract Host getRoom();

	@Override
	public void handlerAdded(ChannelCtx ctx) { handler = ctx; }

	@Override
	public final void onEvent(ChannelCtx ctx, Event event) throws IOException {
		String id = event.id;
		if (id.equals(Timeout.WRITE_TIMEOUT)) {
			LOGGER.info("[{}] WRITE_TIMEOUT", this);
			ctx.close();
		} else if (id.equals(Timeout.READ_TIMEOUT)) {
			LOGGER.info("[{}] READ_TIMEOUT", this);
			kickWithMessage(ctx, PS_ERROR_TIMEOUT);
		}
	}

	@Override
	public abstract void channelClosed(ChannelCtx ctx) throws IOException;

	public void kickWithMessage(int message) { kickWithMessage(handler, message); }

	final MyHashSet<PipeInfo> pipes = new MyHashSet<>();

	final void sendHeartbeat(ChannelCtx ctx) throws IOException {
		ByteList b = IOUtil.getSharedByteBuf();
		ctx.channelWrite(b.put(P___HEARTBEAT).putLong(System.currentTimeMillis()));
	}

	public abstract CMap serialize();

	@Override
	public String toString() {
		CharList sb = new CharList();
		String s = handler.remoteAddress().toString();
		return sb.append(s.startsWith("/") ? s.substring(1) : s).toStringAndFree();
	}
}