package roj.net.handler;

import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.Event;

import java.io.IOException;

/**
 * @author Roj233
 * @since 2022/5/17 16:22
 */
public class Timeout implements ChannelHandler {
	public static final String READ_TIMEOUT = "timeout:r", WRITE_TIMEOUT = "timeout:w";

	public int writeTimeout, readTimeout, pending;
	public long lastRead, lastWrite;

	public Timeout(int r) { this(r, 0); }
	public Timeout(int r, int w) {
		lastRead = System.currentTimeMillis();
		readTimeout = r;
		writeTimeout = w;
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		ctx.channelRead(msg);
		lastRead = System.currentTimeMillis();
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		if (pending++ == 0) lastWrite = System.currentTimeMillis();
		ctx.channelWrite(msg);
	}

	@Override
	public void channelFlushed(ChannelCtx ctx) {
		lastWrite = System.currentTimeMillis();
		pending--;
	}

	@Override
	public void channelTick(ChannelCtx ctx) throws IOException {
		long time = System.currentTimeMillis();
		if (readTimeout > 0 && time - lastRead > readTimeout) {
			if (ctx.postEvent(READ_TIMEOUT).getResult() != Event.RESULT_DENY)
				closeChannel(ctx);
			lastRead = time;
		}
		if (writeTimeout > 0 && pending > 0 && time - lastWrite > writeTimeout) {
			if (ctx.postEvent(WRITE_TIMEOUT).getResult() != Event.RESULT_DENY)
				closeChannel(ctx);
			lastWrite = time;
		}
	}

	protected void closeChannel(ChannelCtx ctx) throws IOException { ctx.close(); }
}