package roj.plugins.cross.server;

import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.Event;
import roj.util.ByteList;

import java.io.IOException;

/**
 * @author Roj233
 * @since 2022/1/24 14:12
 */
public class Pinger implements ChannelHandler {
	final long key;
	int state;

	public Pinger(long key) { this.key = key; }

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		state = 1;

		ByteList b = IOUtil.getSharedByteBuf();
		ctx.channelWrite(b.putLong(key));
		ctx.channel().closeGracefully();
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		state = -1;
		ctx.close();
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		if (state == 0) state = -1;
	}
}
