package roj.net.ch.handler;

import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

/**
 * @author Roj234
 * @since 2022/6/1 23:20
 */
public class Compress implements ChannelHandler {
	public static final int F_AUTO_MERGE = 1, F_PER_PACKET_RESET = 2, F_PER_INPUT_RESET = 4;
	private static final int F_CONTINUOUS = 8;

	private static final int MAX_SPLIT_PACKET = 20;
	public static final byte[] SYNC_END = {0, 0, -1, -1};

	private Deflater def;
	private Inflater inf;

	private int max, thr, buf;
	private byte flag;

	private int rPkt, wPkt;
	private DynByteBuf merged;

	public Compress() {
		this(Deflater.DEFAULT_COMPRESSION);
	}

	public Compress(int lvl) {
		this(8192, 256, 4096, lvl);
	}

	public Compress(int max, int thr, int buf, int lvl) {
		this.max = max;
		this.thr = thr;
		this.buf = buf;
		this.flag = F_AUTO_MERGE | F_PER_INPUT_RESET;

		if (thr < max) {
			this.def = new Deflater(lvl, true);
			this.inf = new Inflater(true);
		}
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf in = (DynByteBuf) msg;
		int len = in.readVarInt(false);
		if (len == 0) {
			ctx.channelRead(in);
			return;
		}
		len += thr;

		if (in.readableBytes() < thr) throw new ZipException("Length(" + len + ") < Zip(" + thr + ")");
		if (len > max + MAX_SPLIT_PACKET) throw new ZipException("Length(" + len + ") > Max(" + max + ")");

		DynByteBuf tmp1 = null;
		if (in.hasArray()) {
			inf.setInput(in.array(), in.arrayOffset() + in.rIndex, in.readableBytes());
			in.skipBytes(in.readableBytes());
		} else {
			tmp1 = ctx.allocate(false, Math.min(buf, in.readableBytes()));
		}

		DynByteBuf out = ctx.allocate(false, len);
		int v = 0;

		try {
			do {
				while (!inf.needsInput()) {
					try {
						int j = inf.inflate(out.array(), out.arrayOffset() + v, len - v);
						v += j;
						if (j == 0) break;
					} catch (DataFormatException e) {
						throw new ZipException(e.getMessage());
					}
				}

				if (in == null) break;

				if (!in.isReadable()) {
					inf.setInput(SYNC_END);
					in = null;
					continue;
				}

				int cnt = Math.min(in.readableBytes(), tmp1.capacity());
				in.read(tmp1.array(), tmp1.arrayOffset(), cnt);
				inf.setInput(tmp1.array(), tmp1.arrayOffset(), cnt);
			} while (true);

			if (v != len) {
				if (wPkt != 0) throw new ZipException("解压的长度(" + v + ") != 包头的长度(" + len + ")");

				wPkt = len - v;
				if (wPkt < 0 || wPkt > MAX_SPLIT_PACKET) throw new ZipException("解压的长度(" + v + ") != 包头的长度(" + len + ")");
			}

			out.wIndex(v);
			if (wPkt > 0 && (flag & F_AUTO_MERGE) != 0) {
				merge(ctx, out);
				out = null;

				if (--wPkt == 0) {
					out = merged;
					merged = null;
					ctx.channelRead(out);
					ctx.reserve(out);
				}
			} else {
				ctx.channelRead(out);
				if (wPkt > 0) wPkt--;
			}
		} finally {
			if ((flag & F_PER_PACKET_RESET) != 0) {
				inf.reset();
			} else if (wPkt == 0 && (flag & F_PER_INPUT_RESET) != 0) {
				inf.reset();
			}
			if (tmp1 != null) ctx.reserve(tmp1);
			if (out != null) ctx.reserve(out);
		}
	}

	private void merge(ChannelCtx ctx, DynByteBuf out) {
		DynByteBuf m = merged;
		if (m != null) {
			if (m.writableBytes() < out.readableBytes()) {
				DynByteBuf tmp = ctx.allocate(true, m.readableBytes() + out.readableBytes());
				tmp.put(m).put(out);

				ctx.reserve(m);
				m = tmp;
			} else {
				m.put(out);
			}
			merged = m.compact();

			ctx.reserve(out);
		} else {
			merged = out;
		}
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf in = (DynByteBuf) msg;

		if (in.readableBytes() > max) {
			rPkt = 1 + in.readableBytes() / max;
			if (rPkt > MAX_SPLIT_PACKET) throw new IOException("数据包过大");
			flag |= F_CONTINUOUS;
			try {
				while (in.readableBytes() > max) {
					channelWrite(ctx, in.slice(max));
				}
			} finally {
				flag ^= F_CONTINUOUS;
			}
		}

		DynByteBuf out;
		if (in.readableBytes() <= thr) {
			out = ctx.allocate(false, in.readableBytes() + 1);
			out.put((byte) 0).put(in);
			try {
				ctx.channelWrite(out);
			} finally {
				ctx.reserve(out);
			}
			return;
		}

		out = ctx.allocate(false, Math.min(in.readableBytes(), max));
		out.putVarInt(in.readableBytes() - thr + rPkt, false);
		rPkt = 0;

		DynByteBuf tmp1 = null;
		if (in.hasArray()) {
			def.setInput(in.array(), in.arrayOffset() + in.rIndex, in.readableBytes());
			in.skipBytes(in.readableBytes());
		} else {
			tmp1 = ctx.allocate(false, Math.min(buf, in.readableBytes()));
		}

		try {
			int v = out.wIndex();
			do {
				while (!def.needsInput()) {
					int i = def.deflate(out.array(), out.arrayOffset() + v, out.capacity() - v);

					if ((v += i) == out.capacity()) {
						out.rIndex = v = 0;
						out.wIndex(out.capacity());
						ctx.channelWrite(out);
					} else if (i == 0) break;
				}

				if (!in.isReadable()) break;

				int cnt = Math.min(in.readableBytes(), tmp1.capacity());
				in.read(tmp1.array(), tmp1.arrayOffset(), cnt);
				def.setInput(tmp1.array(), tmp1.arrayOffset(), cnt);
			} while (true);

			while (true) {
				int i = def.deflate(out.array(), out.arrayOffset() + v, out.capacity() - v, Deflater.SYNC_FLUSH);

				if ((v += i) == out.capacity()) {
					out.rIndex = 0;
					if ((flag & F_CONTINUOUS) != 0) v -= 4;
					out.wIndex(v);
					ctx.channelWrite(out);

					v = 0;
				} else if (i == 0) break;
			}

			if (v > 0) {
				out.rIndex = 0;
				if ((flag & F_CONTINUOUS) != 0) v -= 4;
				out.wIndex(v);
				ctx.channelWrite(out);
			}
		} finally {
			if ((flag & F_PER_PACKET_RESET) != 0) {
				def.reset();
			} else if ((flag & (F_PER_INPUT_RESET | F_CONTINUOUS)) == F_PER_INPUT_RESET) {
				def.reset();
			}

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

	public void setCompressionThreshold(int t) {
		this.thr = t;
	}

	public void setMaxBufferSize(int buf) {
		this.buf = buf;
	}

	public void setSplitThreshold(int t) {
		this.max = t;
	}

	public void setFlag(byte flag) {
		this.flag = flag;
	}
}
