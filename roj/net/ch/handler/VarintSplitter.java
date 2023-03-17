package roj.net.ch.handler;

import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj233
 * @since 2022/5/17 12:48
 */
public final class VarintSplitter implements ChannelHandler {
	private static final byte[] LEN = new byte[65];
	static {
		for (int i = 0; i < 64; ++i) {
			LEN[i] = (byte) Math.ceil((63d - (i - 1)) / 7d);
		}
		LEN[64] = 1;
	}
	public static int getVarIntLength(int value) { return LEN[32 + Integer.numberOfLeadingZeros(value)]; }
	public static int getVarIntLength(long value) { return LEN[Long.numberOfLeadingZeros(value)]; }

	private static VarintSplitter dvi, dvlui;
	public static ChannelHandler twoMbVI() {
		if (dvi == null) dvi = new VarintSplitter(3, false);
		return dvi;
	}
	public static ChannelHandler twoMbVLUI() {
		if (dvlui == null) dvlui = new VarintSplitter(3, true);
		return dvlui;
	}

	private final int maxLengthBytes;
	private final boolean vlui;

	public VarintSplitter(int maxLengthBytes) {
		this(maxLengthBytes, false);
	}

	public VarintSplitter(int maxLengthBytes, boolean vlui) {
		super();
		this.maxLengthBytes = maxLengthBytes;
		this.vlui = vlui;
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf data = (DynByteBuf) msg;

		int r = data.readableBytes();

		int len = getVarIntLength(r);
		if (len > maxLengthBytes) throw new IllegalArgumentException("packet too large " + r);

		DynByteBuf buf = ctx.alloc().expand(data, len, false, false);

		int i = buf.wIndex();
		buf.wIndex(0);

		if (vlui) buf.putVUInt(r);
		else buf.putVarInt(r, false);

		buf.wIndex(i);

		try {
			ctx.channelWrite(buf);
		} finally {
			if (data != buf) ctx.reserve(buf);
		}
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf data = (DynByteBuf) msg;
		int pos = data.rIndex;
		int lim = data.wIndex();

		while (data.isReadable()) {
			int len = vlui ? readVUInt(data, maxLengthBytes) : readVarInt(data, maxLengthBytes);

			if (len >= 0 && data.readableBytes() >= len) {
				data.wIndex(pos = data.rIndex + len);
				try {
					ctx.channelRead(data);
				} finally {
					if (data.capacity() > 0) {
						data.wIndex(lim);
						if (data.rIndex < pos) System.err.println("not fully process packet, remains " + data.dump());
						data.rIndex = pos;
					}
				}
			} else {
				data.rIndex(pos);
				data.compact();
				return;
			}
		}
	}

	public static int readVarInt(DynByteBuf data, int maxBytes) {
		int pos = data.rIndex;
		int length = 0;
		int i = 0;

		byte b;
		do {
			if (!data.isReadable()) return -1;

			b = data.readByte();
			length |= (b & 0x7F) << (i++ * 7);
			if (i > maxBytes) throw new IllegalArgumentException("packet too large " + length);
		} while ((b & 0x80) != 0);
		return length;
	}

	public static int readVUInt(DynByteBuf data, int maxBytes) {
		int b = data.readUnsignedByte();
		if ((b&0x80) == 0) return b;

		if ((b&0x40) == 0) {
			if (2 > maxBytes) throw new IllegalArgumentException("数据包过大");
			if (data.readableBytes() < 1) return -1;

			return ((b&0x3F)<< 8) | data.readUnsignedByte();
		}
		if ((b&0x20) == 0) {
			if (3 > maxBytes) throw new IllegalArgumentException("数据包过大");
			if (data.readableBytes() < 2) return -1;

			return ((b&0x1F)<<16) | data.readUShortLE();
		}
		if ((b&0x10) == 0) {
			if (4 > maxBytes) throw new IllegalArgumentException("数据包过大");
			if (data.readableBytes() < 3) return -1;

			return ((b&0x0F)<<24) | data.readMediumLE();
		}
		if ((b&0x08) == 0) {
			if (5 > maxBytes) throw new IllegalArgumentException("数据包过大");
			if (data.readableBytes() < 4) return -1;

			if ((b&7) == 0) return data.readIntLE();
		}

		throw new IllegalArgumentException("数据包过大: " + Integer.toHexString(b));
	}
}
