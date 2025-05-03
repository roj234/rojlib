package roj.plugins.minecraft.server.network;

import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.plugins.minecraft.server.MinecraftServer;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/3/22 1:21
 */
public class FlowControl implements ChannelHandler {
	private long lastTime = System.currentTimeMillis();
	private int bucket = 100;

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		long time = System.currentTimeMillis();
		int newPackets = (int) (time - lastTime);
		lastTime = time;
		bucket = Math.min(250, bucket+newPackets) - 1;

		if (bucket < 0) {
			PlayerConnection connection = ctx.attachment(MinecraftServer.PLAYER);
			if (connection != null) connection.disconnect("您发包速度太快了");
			else ctx.close();
			return;
		}

		ctx.channelRead(msg);
	}
}