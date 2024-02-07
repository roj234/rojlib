package roj.net.ch.handler;

import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.Event;
import roj.util.Identifier;

import java.io.IOException;

/**
 * @author Roj233
 * @since 2022/5/17 16:22
 */
public class Timeout implements ChannelHandler {
	public static final String ID = "timer";
	public static final Identifier READ_TIMEOUT = Identifier.of(ID, "r"), WRITE_TIMEOUT = Identifier.of(ID, "w");

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
