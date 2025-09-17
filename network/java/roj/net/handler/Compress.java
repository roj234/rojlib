package roj.net.handler;

import roj.io.BufferPool;
import roj.io.CorruptedInputException;
import roj.net.ChannelCtx;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * @author Roj234
 * @since 2022/6/1 23:20
 */
public class Compress extends GDeflate {
	public static final int F_PER_INPUT_RESET = 1;

	public static final byte[] _FULL_FLUSH = {0, 0, -1, -1};

	private int max, thr;
	private byte flag;

	private DynByteBuf merged;
	private int prevLen;

	public Compress() { this(Deflater.DEFAULT_COMPRESSION); }
	public Compress(int lvl) { this(2097152, 256, 4096, lvl); }

	public Compress(int max, int thr, int buf, int lvl) {
		this.max = max;
		this.thr = thr;
		this.buf = buf;

		if (thr < max) {
			this.def = new Deflater(lvl, true);
			this.inf = new Inflater(true);
		}
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf in = (DynByteBuf) msg;
		int type = in.readUnsignedByte();
		if (type == 0) {
			ctx.channelRead(in);
			return;
		}

		DynByteBuf out;
		if (merged != null) {
			out = merged;
			if (type == 1) merged = null;
		} else {
			int len = in.readVUInt() + thr;
			if (len > max) throw new IOException("数据包太大:"+len+">"+max);
			prevLen = len;

			out = ctx.allocate(false, len);
			if (type != 1) merged = out;
		}

		try {
			inflateRead(ctx, in, out);
		} finally {
			if (merged == null) {
				if ((flag & F_PER_INPUT_RESET) != 0) inf.reset();
				BufferPool.reserve(out);
			}
		}
	}
	@Override
	protected void readPacket(ChannelCtx ctx, DynByteBuf out) throws IOException {
		if (out.readableBytes() != prevLen) {
			if (merged != null && out.readableBytes() < prevLen) return;
			throw new CorruptedInputException("解压的长度("+out.readableBytes()+") != 包头的长度("+prevLen+")");
		}
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf in = (DynByteBuf) msg;
		if (in.readableBytes() > max) throw new IOException("数据包太大:"+in.readableBytes()+">"+max);

		DynByteBuf out;
		if (in.readableBytes() <= thr) {
			out = ctx.alloc().expand(in, 1, false, false);
			try {
				ctx.channelWrite(out.set(0, /*UNCOMPRESSED*/0));
			} finally {
				if (out != in) BufferPool.reserve(out);
			}
			return;
		}

		out = ctx.allocate(false, Math.min(buf, in.readableBytes()+5)).put(/*TERMINATE*/1).putVUInt(in.readableBytes() - thr);
		try {
			deflateWrite(ctx, in, out, 1);
		} finally {
			if ((flag & F_PER_INPUT_RESET) != 0) def.reset();
			BufferPool.reserve(out);
		}
	}
	@Override
	protected int writePacket(ChannelCtx ctx, DynByteBuf out, boolean isFull) throws IOException {
		out.set(0, isFull ? /*CONTINUOUS*/2 : /*TERMINATE*/1);
		ctx.channelWrite(out);
		return 1;
	}

	@Override
	public void handlerRemoved(ChannelCtx ctx) {
		super.handlerRemoved(ctx);
		if (merged != null) {
			BufferPool.reserve(merged);
			merged = null;
		}
	}
}