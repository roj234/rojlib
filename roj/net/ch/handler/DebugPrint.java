package roj.net.ch.handler;

import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2022/10/10 0010 9:52
 */
public class DebugPrint implements ChannelHandler {
	private final String ID;

	public DebugPrint(String id) {ID = id;}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		System.out.println(ID+"-Read " + msg);
		ctx.channelRead(msg);
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		System.out.println(ID+"-Write " + msg);
		ctx.channelWrite(msg);
	}
}
