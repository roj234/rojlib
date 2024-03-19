package roj.net.proto_obf;

import roj.crypt.CRC32s;
import roj.io.buf.BufferPool;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.handler.PacketMerger;
import roj.util.ArrayRef;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.Random;

/**
 * @author Roj234
 * @since 2023/4/26 0026 23:19
 */
public final class Obfuscate extends PacketMerger implements ChannelHandler {
	private int cByte = -1, sByte = -1;

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = (DynByteBuf) msg;
		if (sByte == -1) {
			sByte = buf.readUnsignedByte();
			sByte = CRC32s.update(sByte, 0);
		}

		ArrayRef arr = buf.byteRange(buf.rIndex, buf.readableBytes());

		int cc = sByte;
		for (int i = 0; i < arr.length; i++) {
			int v = arr.get(i) ^ cc;
			arr.set(i, v);
			cc = CRC32s.update(cc, v);
		}
		sByte = cc;

		mergedRead(ctx, buf);
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = (DynByteBuf) msg;
		if (cByte == -1) {
			cByte = new Random().nextInt(256);

			buf = ctx.alloc().expand(buf, 1, false, false).put(0, 0);
		}

		ArrayRef arr = buf.byteRange(buf.rIndex, buf.readableBytes());

		int cc = cByte;
		for (int i = 0; i < arr.length; i++) {
			int v = arr.get(i);
			arr.set(i, v ^ cc);
			cc = CRC32s.update(cc, v);
		}
		cByte = cc;

		try {
			ctx.channelWrite(buf);
		} finally {
			if (msg != buf) BufferPool.reserve(buf);
		}
	}
}