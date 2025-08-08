package roj.net.handler;

import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.MyChannel;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2022/10/11 19:16
 */
public class Pipe2 implements ChannelHandler {
	public static final ThreadLocal<Pipe2> WRITER = new ThreadLocal<>();
	public Object att;

	MyChannel remote, local;
	boolean dispatchClose;

	public Pipe2(MyChannel remote, boolean dispatchClose) {
		this.remote = remote;
		this.dispatchClose = dispatchClose;
	}

	public void setDispatchClose(boolean dispatchClose) {this.dispatchClose = dispatchClose;}
	public MyChannel getRemote() {return remote;}
	public MyChannel getLocal() {return local;}
	private MyChannel PAIR(ChannelCtx ctx) {return ctx.channel() == remote ? local : remote;}

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

		Pipe2 prev = WRITER.get();
		WRITER.set(this);
		try {
			PAIR(ctx).fireChannelWrite(msg);
		} finally {
			WRITER.set(prev);
		}
	}

	//肥肠简单的拥塞控制
	@Override public void channelFlushing(ChannelCtx ctx) {PAIR(ctx).readInactive();}
	@Override public void channelFlushed(ChannelCtx ctx) {PAIR(ctx).readActive();}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		MyChannel ch = ctx.channel() == remote ? local : remote;
		if (ch == remote || dispatchClose) {
			Pipe2 prev = WRITER.get();
			WRITER.set(this);
			try {
				IOUtil.closeSilently(ch);
			} finally {
				WRITER.set(prev);
			}
		}
	}

	public void close() {
		IOUtil.closeSilently(remote);
		if (dispatchClose) IOUtil.closeSilently(local);
	}
}