package roj.net.ch.handler;

import roj.crypt.CRCAny;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.Random;

/**
 * @author Roj234
 * @since 2023/4/26 0026 23:19
 */
public class Obfuscate implements ChannelHandler {
	int cByte = -1, sByte = -1;

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = (DynByteBuf) msg;
		if (sByte == -1) {
			sByte = buf.readUnsignedByte();
			sByte = CRCAny.CRC_32.update(sByte, 0);
		}

		int cc = sByte;
		int r = buf.rIndex;
		int e = r + buf.readableBytes();
		while (r < e) {
			int v = buf.get(r) ^ cc;
			buf.put(r, v);
			cc = CRCAny.CRC_32.update(cc, v);

			r++;
		}
		sByte = cc;
		ctx.channelRead(buf);
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = (DynByteBuf) msg;
		if (cByte == -1) {
			cByte = new Random().nextInt(256);

			buf = ctx.alloc().expand(buf, 1, false, false).put(0, 0);
		}

		int cc = cByte;
		int r = buf.rIndex;
		int e = r + buf.readableBytes();
		while (r < e) {
			byte v = buf.get(r);
			buf.put(r, v ^ cc);
			cc = CRCAny.CRC_32.update(cc, v);

			r++;
		}
		cByte = cc;

		try {
			ctx.channelWrite(buf);
		} finally {
			if (msg != buf) ctx.reserve(buf);
		}
	}
}
