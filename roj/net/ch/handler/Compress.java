package roj.net.ch.handler;

import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * @author Roj234
 * @since 2022/6/1 23:20
 */
public class Compress implements ChannelHandler {
	public static final int F_PER_INPUT_RESET = 1;

	public static final byte[] SYNC_END = {0, 0, -1, -1};

	private Deflater def;
	private Inflater inf;

	private int max, thr, buf;
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

		int len = merged == null ? in.readVUInt() + thr : prevLen;
		if (len > max) throw new IOException("Length("+len+") > Max("+max+")");

		boolean hasMorePacket = type == 2;

		DynByteBuf tmp1 = null;
		if (in.hasArray()) {
			inf.setInput(in.array(), in.arrayOffset() + in.rIndex, in.readableBytes());
			in.rIndex = in.wIndex();
		} else {
			tmp1 = ctx.allocate(false, Math.min(buf, in.readableBytes()));
		}

		DynByteBuf out = merged != null ? merged : ctx.allocate(false, len);
		int v = out.wIndex();

		try {
			while (true) {
				while (true) {
					try {
						int j = inf.inflate(out.array(), out.arrayOffset() + v, out.capacity() - v);
						v += j;
						if (j == 0) break;
					} catch (DataFormatException e) {
						throw new IOException(e.getMessage());
					}
				}

				if (!in.isReadable()) break;

				int cnt = Math.min(in.readableBytes(), tmp1.capacity());
				in.read(tmp1.array(), tmp1.arrayOffset(), cnt);
				inf.setInput(tmp1.array(), tmp1.arrayOffset(), cnt);
			}

			out.wIndex(v);
			if (!hasMorePacket) {
				merged = null;

				if (v != len) throw new IOException("解压的长度("+v+") != 包头的长度("+len+")");
				ctx.channelRead(out);
			} else {
				merged = out;
				prevLen = len;
				out = null;
			}
		} finally {
			if (tmp1 != null) ctx.reserve(tmp1);
			if (out != null) {
				if ((flag & F_PER_INPUT_RESET) != 0) inf.reset();

				ctx.reserve(out);
			}
		}
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf in = (DynByteBuf) msg;

		DynByteBuf out;
		if (in.readableBytes() <= thr) {
			out = ctx.alloc().expand(in, 1, false, false);
			try {
				ctx.channelWrite(out.put(0, 0));
			} finally {
				if (out != in) ctx.reserve(out);
			}
			return;
		}

		out = ctx.allocate(false, Math.min(buf, in.readableBytes()+5))
				 .put(1).putVUInt(in.readableBytes() - thr);

		DynByteBuf tmp1 = null;
		if (in.hasArray()) {
			def.setInput(in.array(), in.arrayOffset()+in.rIndex, in.readableBytes());
			in.rIndex = in.wIndex();
		} else {
			tmp1 = ctx.allocate(false, Math.min(buf, in.readableBytes()));
		}

		try {
			int v = out.wIndex();
			while (true) {
				int flush = in.isReadable() ? Deflater.NO_FLUSH : Deflater.SYNC_FLUSH;
				while (true) {
					int i = def.deflate(out.array(), out.arrayOffset() + v, out.capacity() - v, flush);

					if ((v += i) == out.capacity()) {
						out.put(0, 2);
						out.rIndex = 0;
						out.wIndex(v);
						ctx.channelWrite(out);

						v = 1;
					} else if (i == 0) break;
				}

				if (!in.isReadable()) break;

				int cnt = Math.min(in.readableBytes(), tmp1.capacity());
				in.read(tmp1.array(), tmp1.arrayOffset(), cnt);
				def.setInput(tmp1.array(), tmp1.arrayOffset(), cnt);
			}

			if (v > 0) {
				out.put(0, 1);
				out.rIndex = 0;
				out.wIndex(v);
				ctx.channelWrite(out);
			}
		} finally {
			if ((flag & F_PER_INPUT_RESET) != 0) def.reset();

			if (tmp1 != null) ctx.reserve(tmp1);
			ctx.reserve(out);
		}
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		if (def != null) {
			def.end();
			inf.end();
		}
		if (merged != null) {
			ctx.reserve(merged);
			merged = null;
		}
	}
}
