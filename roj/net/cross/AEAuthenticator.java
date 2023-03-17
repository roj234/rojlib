package roj.net.cross;

import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.util.DynByteBuf;

import java.io.IOException;

import static roj.net.cross.Util.*;

/**
 * @author Roj233
 * @since 2022/5/17 21:14
 */
class AEAuthenticator implements ChannelHandler {
	private final byte[] data;
	private final byte protocol;

	AEAuthenticator(byte[] data, int protocol) {
		this.data = data;
		this.protocol = (byte) protocol;
	}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		DynByteBuf buf = ctx.allocate(true, 6 + data.length);
		buf.putInt(MAGIC).put((byte) PROTOCOL_VERSION).put(protocol).put(data);
		try {
			ctx.channelWrite(buf);
		} finally {
			ctx.reserve(buf);
		}
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf rb = (DynByteBuf) msg;

		int j = rb.get() & 0xFF;
		if (j != PC_LOGON_H) {
			if (j == P_LOGOUT) {
				print("服务端断开连接");
			} else {
				if (j > 100 && j - 100 < HS_ERROR_NAMES.length) {
					print("握手: " + HS_ERROR_NAMES[j - 100]);
				} else {
					rb.rIndex--;
					IAEClient.onError(rb, null);
				}
			}
			ctx.close();
			return;
		}

		ctx.removeSelf();
		ctx.channelOpened();
		ctx.channelRead(msg);
	}
}
