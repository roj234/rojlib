package roj.net.ch.handler;

import roj.net.ch.ChannelCtx;
import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj233
 * @since 2022/5/17 12:48
 */
public class VarintSplitter extends PacketMerger {
	private static final byte[] LEN = new byte[65];
	static {
		for (int i = 0; i < 64; ++i) {
			LEN[i] = (byte) Math.ceil((63d - (i - 1)) / 7d);
		}
		LEN[64] = 1;
	}

	private final int maxVarIntByte;
	private final boolean mergeRemain;

	public VarintSplitter(int maxVarIntByte) {
		this(maxVarIntByte, false);
	}

	public VarintSplitter(int maxVarIntByte, boolean mergeRemain) {
		super();
		this.maxVarIntByte = maxVarIntByte;
		this.mergeRemain = mergeRemain;
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf data = (DynByteBuf) msg;
		int r = data.readableBytes();
		if (r == 0) throw new IllegalArgumentException("length==0");
		int len = getVarIntLength(r);
		if (len > maxVarIntByte) {
			throw new IllegalArgumentException("packet too large " + r);
		}

		DynByteBuf buf = ctx.allocate(true, r + len);
		while (r >= 0x80) {
			buf.put((byte) ((r & 0x7F) | 0x80));
			r >>>= 7;
		}
		buf.put((byte) r).put(data);

		try {
			ctx.channelWrite(buf);
		} finally {
			ctx.reserve(buf);
		}
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf data = (DynByteBuf) msg;
		int pos = data.rIndex;

		while (true) {
			int length = (int) readVarInt(data, maxVarIntByte);
			if (length < 0) return;

			if (data.readableBytes() >= length) {
				int lim = data.wIndex();
				data.wIndex(pos = data.rIndex + length);
				try {
					if (mergeRemain) {
						mergedRead(ctx, data);
					} else {
						ctx.channelRead(data);
					}
				} finally {
					if (data.capacity() > 0) {
						data.wIndex(lim);
						data.rIndex(pos);
					}
				}
			} else {
				data.rIndex(pos);
				return;
			}
		}
	}

	public static long readVarInt(DynByteBuf data, int maxBytes) {
		int pos = data.rIndex;
		long length = 0;
		int i = 0;

		byte b;
		do {
			if (!data.isReadable()) {
				data.rIndex(pos);
				data.compact();
				return -1;
			}

			b = data.readByte();
			if (b == 0) throw new IllegalArgumentException("length==0");
			length |= (b & 0x7F) << (i++ * 7);
			if (i > maxBytes) {
				throw new IllegalArgumentException("packet too large " + length);
			}
		} while ((b & 0x80) != 0);
		return length;
	}

	public static int getVarIntLength(int value) {
		return LEN[32 + Integer.numberOfLeadingZeros(value)];
	}

	public static int getVarIntLength(long value) {
		return LEN[Long.numberOfLeadingZeros(value)];
	}
}
