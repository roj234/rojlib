package roj.net.cross.server;

import roj.net.ch.ChannelCtx;

import java.io.IOException;

import static roj.net.cross.Util.P_LOGOUT;
import static roj.net.cross.Util.print;

/**
 * @author Roj233
 * @since 2021/12/21 13:43
 */
final class Logout extends Stated {
	static final Logout LOGOUT = new Logout();
	static final Logout REQUESTED = new Logout();

	@Override
	public void handlerAdded(ChannelCtx ctx) {
		try {
			ChannelCtx.bite(ctx, (byte) P_LOGOUT);
			if (this == REQUESTED) {
				ctx.flush();
				ctx.close();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		print(ctx.attachment(Client.CLIENT) + ": 断开");
		ctx.close();
	}
}
