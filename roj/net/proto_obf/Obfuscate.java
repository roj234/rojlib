package roj.net.proto_obf;

import roj.crypt.CRCAny;
import roj.io.buf.BufferPool;
import roj.io.buf.NativeArray;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.handler.PacketMerger;
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
			sByte = CRCAny.CRC_32.update(sByte, 0);
		}

		NativeArray arr = buf.byteRange(buf.rIndex, buf.readableBytes());

		int cc = sByte;
		for (int i = 0; i < arr.length(); i++) {
			int v = arr.get(i) ^ cc;
			arr.set(i, v);
			cc = CRCAny.CRC_32.update(cc, v);
		}
		sByte = cc;

		mergedRead(ctx, buf);
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = (DynByteBuf) msg;
		if (cByte == -1) {
			cByte = new Random().nextInt(256);

			buf = BufferPool.expand(buf, 1, false, false).put(0, 0);
		}

		NativeArray arr = buf.byteRange(buf.rIndex, buf.readableBytes());

		int cc = cByte;
		for (int i = 0; i < arr.length(); i++) {
			int v = arr.get(i);
			arr.set(i, v ^ cc);
			cc = CRCAny.CRC_32.update(cc, v);
		}
		cByte = cc;

		try {
			ctx.channelWrite(buf);
		} finally {
			if (msg != buf) BufferPool.reserve(buf);
		}
	}
}
