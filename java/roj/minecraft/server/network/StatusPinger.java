package roj.minecraft.server.network;

import roj.io.IOUtil;
import roj.minecraft.server.MinecraftServer;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/3/19 0019 16:04
 */
final class StatusPinger implements ChannelHandler {
	StatusPinger() {}
	private boolean metadataRequested;

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		Packet packet = (Packet) msg;
		switch (packet.name) {
			case "QueryRequest":
				if (metadataRequested) ctx.channel().closeGracefully();
				metadataRequested = true;
				ctx.channelWrite(new Packet("QueryResponse", MinecraftServer.INSTANCE.getMetaBytes()));
			break;
			case "QueryPing":
				ctx.channelWrite(new Packet("QueryPong", IOUtil.getSharedByteBuf().putLong(packet.getData().readLong())));
				ctx.channel().closeGracefully();
			break;
		}
	}
}