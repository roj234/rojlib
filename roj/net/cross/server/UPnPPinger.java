package roj.net.cross.server;

import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.Event;
import roj.util.ByteList;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Roj233
 * @since 2022/1/24 14:12
 */
public class UPnPPinger implements ChannelHandler {
	final long sec;
	int state;

	public UPnPPinger(long sec) {
		this.sec = sec;
	}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		// todo
		ByteList tmp = IOUtil.getSharedByteBuf();
		tmp.putVarIntUTF("MSS UPNP PING TEST");
		ctx.channelWrite(tmp);
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		ByteBuffer rb = (ByteBuffer) msg;
		state = rb.getLong() == sec ? 1 : -1;
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		ctx.close();
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		if (state == 0) state = -1;
	}
}
