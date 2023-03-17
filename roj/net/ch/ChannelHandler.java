package roj.net.ch;

import java.io.IOException;

/**
 * @author Roj233
 * @since 2022/1/8 18:19
 */
public interface ChannelHandler {
	default void handlerAdded(ChannelCtx ctx) {}
	default void handlerRemoved(ChannelCtx ctx) {}

	default void channelOpened(ChannelCtx ctx) throws IOException {
		ctx.channelOpened();
	}
	default void channelClosed(ChannelCtx ctx) throws IOException {}

	default void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		ctx.channelWrite(msg);
	}
	default void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		ctx.channelRead(msg);
	}

	default void channelFlushed(ChannelCtx ctx) throws IOException {}

	default void onEvent(ChannelCtx ctx, Event event) throws IOException {}

	default void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {
		ctx.exceptionCaught(ex);
	}

	default void channelTick(ChannelCtx ctx) throws IOException {}
}
