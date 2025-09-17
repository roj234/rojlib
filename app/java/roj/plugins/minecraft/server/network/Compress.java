package roj.plugins.minecraft.server.network;

import roj.io.BufferPool;
import roj.io.CorruptedInputException;
import roj.net.ChannelCtx;
import roj.net.handler.GDeflate;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * @author Roj234
 * @since 2024/3/19 22:21
 */
public class Compress extends GDeflate {
	private final int thr;
	public Compress(int thr) {
		this.thr = thr;
		this.buf = 1024;
		this.inf = new Inflater();
		this.def = new Deflater();
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = (DynByteBuf) msg;
		if (buf.isReadable()) {
			int uncompLen = buf.readVarInt();
			if (uncompLen == 0) {
				ctx.channelRead(buf);
			} else {
				if (uncompLen < thr) throw new IOException("Badly compressed packet - size of "+uncompLen+" is below server threshold of "+thr);
				if (uncompLen > 8388608) throw new IOException("Badly compressed packet - size of"+uncompLen+" is larger than protocol maximum of 8388608");

				var out = ctx.allocate(false, uncompLen);
				try {
					inflateRead(ctx, buf, out);
				} finally {
					BufferPool.reserve(out);
				}
			}
		}
	}
	@Override
	protected void readPacket(ChannelCtx ctx, DynByteBuf out) throws IOException {
		if (out.readableBytes() != out.capacity()) throw new CorruptedInputException();
		ctx.channelRead(out);
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = (DynByteBuf) msg;
		if (buf.readableBytes() < thr) {
			if (!buf.isReadable()) ctx.channelWrite(buf);
			else {
				DynByteBuf out = ctx.alloc().expandBefore(buf, 1);
				try {
					out.set(0, 0);
					ctx.channelWrite(out);
				} finally {
					if (out != buf) BufferPool.reserve(out);
				}
			}
		} else {
			ByteList out = new ByteList();
			try {
				out.putVarInt(buf.readableBytes());
				out.ensureWritable(512);
				def.reset();
				deflateWrite(ctx, buf, out, /*FINISH*/2);
				ctx.channelWrite(out);
			} finally {
				out.release();
			}
		}
	}
	@Override
	protected int writePacket(ChannelCtx ctx, DynByteBuf out, boolean isFull) {
		if (isFull) out.ensureWritable(out.capacity());
		return out.wIndex();
	}
}