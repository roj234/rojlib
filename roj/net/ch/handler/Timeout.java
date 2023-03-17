package roj.net.ch.handler;

import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.Event;
import roj.util.NamespaceKey;

import java.io.IOException;

/**
 * @author Roj233
 * @since 2022/5/17 16:22
 */
public class Timeout implements ChannelHandler {
	public static final String ID = "timer";
	public static final NamespaceKey READ_TIMEOUT = NamespaceKey.of(ID, "r"), WRITE_TIMEOUT = NamespaceKey.of(ID, "w");

	public int writeTimeout, readTimeout, pending;
	public long lastRead, lastWrite;

	public Timeout() {
		lastRead = System.currentTimeMillis();
	}

	public Timeout(int r, int w) {
		this();
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
	public void channelFlushed(ChannelCtx ctx) throws IOException {
		lastWrite = System.currentTimeMillis();
		pending--;
	}

	@Override
	public void channelTick(ChannelCtx ctx) throws IOException {
		long time = System.currentTimeMillis();
		if (time - lastRead > readTimeout) {
			if (ctx.postEvent(READ_TIMEOUT).getResult() != Event.RESULT_DENY)
				ctx.close();
			lastRead = time;
		}
		if (pending > 0 && time - lastWrite > writeTimeout) {
			ctx.postEvent(WRITE_TIMEOUT);
			lastWrite = time;
		}
	}
}
