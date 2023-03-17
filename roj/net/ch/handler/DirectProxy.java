package roj.net.ch.handler;

import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.MyChannel;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2022/10/11 0011 19:16
 */
public class DirectProxy implements ChannelHandler {
	MyChannel a,b;
	boolean dispatchClose;

	public DirectProxy(MyChannel a, boolean dispatchClose) {
		this.a=a;
		this.dispatchClose = dispatchClose;
	}

	@Override
	public void channelOpened(ChannelCtx c) throws IOException {
		if (c.channel() != a) {
			assert b == null;
			b = c.channel();
			a.readActive();
		}
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		assert b != null;
		(ctx.channel() == a?b:a).fireChannelWrite(msg);
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		if (dispatchClose) {
			MyChannel ch = ctx.channel() == a ? b : a;
			if (ch != null) ch.close();
		}
	}
}
