package roj.net.handler;

import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.MyChannel;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2022/10/11 0011 19:16
 */
public class DirectProxy implements ChannelHandler {
	public static final ThreadLocal<DirectProxy> CURRENT_WRITER = new ThreadLocal<>();

	MyChannel remote, local;
	boolean dispatchClose;

	public DirectProxy(MyChannel remote, boolean dispatchClose) {
		this.remote = remote;
		this.dispatchClose = dispatchClose;
	}

	public void setDispatchClose(boolean dispatchClose) {this.dispatchClose = dispatchClose;}
	public MyChannel getRemote() {return remote;}
	public MyChannel getLocal() {return local;}

	@Override
	public void channelOpened(ChannelCtx c) throws IOException {
		if (c.channel() != remote) {
			assert local == null;
			local = c.channel();
			remote.readActive();
		}
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		assert local != null;

		DirectProxy prev = CURRENT_WRITER.get();
		CURRENT_WRITER.set(this);
		try {
			(ctx.channel() == remote ? local : remote).fireChannelWrite(msg);
		} finally {
			CURRENT_WRITER.set(prev);
		}
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		MyChannel ch = ctx.channel() == remote ? local : remote;
		if (ch == remote || dispatchClose) IOUtil.closeSilently(ch);
	}

	public void close() {
		IOUtil.closeSilently(remote);
		if (dispatchClose) IOUtil.closeSilently(local);
	}
}