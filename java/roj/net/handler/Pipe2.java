package roj.net.handler;

import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.MyChannel;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2022/10/11 0011 19:16
 */
public class Pipe2 implements ChannelHandler {
	public static final ThreadLocal<Pipe2> CURRENT_WRITER = new ThreadLocal<>();

	MyChannel remote, local;
	boolean dispatchClose;

	public Object att;

	// FIXME
	public Pipe2(MyChannel remote, boolean dispatchClose) {
		this.remote = remote;
		this.dispatchClose = dispatchClose;
	}

	public void setDispatchClose(boolean dispatchClose) {this.dispatchClose = dispatchClose;}
	public MyChannel getRemote() {return remote;}
	public MyChannel getLocal() {return local;}

	@Override
	public void handlerAdded(ChannelCtx c) {
		if (c.channel() != remote) {
			assert local == null;
			local = c.channel();
		}
	}

	@Override
	public void channelOpened(ChannelCtx c) throws IOException {
		if (c.channel() != remote) remote.readActive();
		c.channelOpened();
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		assert local != null;

		Pipe2 prev = CURRENT_WRITER.get();
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
		if (ch == remote || dispatchClose) {
			System.out.println("dispatch close to "+ch);
			Pipe2 prev = CURRENT_WRITER.get();
			CURRENT_WRITER.set(this);
			try {
				IOUtil.closeSilently(ch);
			} finally {
				CURRENT_WRITER.set(prev);
			}
		}
	}

	public void close() {
		IOUtil.closeSilently(remote);
		if (dispatchClose) IOUtil.closeSilently(local);
	}
}