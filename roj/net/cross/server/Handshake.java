package roj.net.cross.server;

import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.mss.MSSException;
import roj.util.DynByteBuf;

import java.io.IOException;

import static roj.net.cross.Util.*;

/**
 * @author Roj233
 * @since 2021/12/21 13:17
 */
final class Handshake extends Stated {
	static final Stated HANDSHAKE = new Handshake();

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf rb = (DynByteBuf) msg;
		if (rb.readableBytes() < 6) return;

		if (rb.readInt() != MAGIC) {
			ChannelCtx.bite(ctx, (byte) HS_ERR_PROTOCOL);
			ctx.close();
			return;
		}

		int v = rb.get() & 0xFF;
		if (v < PROTOCOL_VERSION) {
			ChannelCtx.bite(ctx, (byte) HS_ERR_VERSION_LOW);
			ctx.close();
			return;
		} else if (v > PROTOCOL_VERSION) {
			ChannelCtx.bite(ctx, (byte) HS_ERR_VERSION_HIGH);
			ctx.close();
			return;
		}

		int type = rb.get() & 0xFF;
		ChannelHandler ch;
		switch (type) {
			case PS_LOGIN_C: ch = ClientLogin.CLIENT_LOGIN; break;
			case PS_LOGIN_H: ch = HostLogin.HOST_LOGIN; break;
			case PS_LOGIN_PIPE: ch = PipeLogin.PIPE_LOGIN; break;
			default: throw new MSSException(33, "无效的角色类型 " + type, null);
		}

		ctx.replaceSelf(ch);
		ch.channelRead(ctx, msg);
	}
}
