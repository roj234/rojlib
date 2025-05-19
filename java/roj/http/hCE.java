package roj.http;

import roj.crypt.CRC32;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.Event;
import roj.net.MyChannel;
import roj.util.DynByteBuf;

import java.io.EOFException;
import java.io.IOException;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

import static java.util.zip.GZIPInputStream.GZIP_MAGIC;

/**
 * Content-Encoding
 * @author Roj234
 * @since 2025/4/27 5:53
 */
public final class hCE implements ChannelHandler {
	public static final String ANCHOR = "http:ce_handler";
	public static final String IN_END = "hce:inEnd", TOO_MANY_DATA = "hce:inOverflow";

	private static final byte DEFLATE_INIT = 0, GZIP_INIT = 1, CHECKSUM = 2, IDENTITY = 6, DEFLATE = 4, GZIP = 5, END = 3;
	private byte state;
	private long readLimit;
	private boolean exactLimit;

	private int crc, i32rcv;

	private hCE() {}
	public static ChannelCtx apply(ChannelCtx ctx, Headers headers, boolean skipDecode, long readLimit) {
		String contentEncoding = skipDecode ? "identity" : headers.getContentEncoding();
		byte state = switch (contentEncoding) {
			case "identity" -> IDENTITY;
			case "deflate" -> DEFLATE_INIT;
			case "gzip" -> GZIP_INIT;
			default -> throw new UnsupportedOperationException("不支持的编码方式:" + contentEncoding);
		};
		long contentLength = headers.getContentLength();

		hCE hce;
		var exist = ctx.channel().handler(ANCHOR);

		if (exist == null) {
			ctx.channel().addBefore(ctx, ANCHOR, hce = new hCE());
			exist = ctx.channel().handler(ANCHOR);
		} else {
			hce = (hCE) exist.handler();
		}

		hce.state = state;
		hce.exactLimit = contentLength >= 0;
		hce.readLimit = contentLength >= 0 ? contentLength : readLimit;

		return exist;
	}
	public static void reset(ChannelCtx ctx) {
		hCE hce;
		var exist = ctx.channel().handler(ANCHOR);
		if (exist != null) {
			hce = (hCE) exist.handler();
			hce.state = END;
			hce.exactLimit = false;
			hce.readLimit = Long.MAX_VALUE;
			hDeflate.inflate(ctx, null, false);
		}
	}

	@Override
	public void handlerRemoved(ChannelCtx ctx) {
		ctx.channel().remove(hDeflate.ANCHOR);
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id.equals(hDeflate.IN_EOF)) {
			var comp = (hDeflate) event.getData();
			var inf = comp.getInf();
			comp.setInf(null);

			i32rcv = inf.getTotalOut();

			inf.end();

			if (state == GZIP) {
				state = CHECKSUM;
				crc = comp.getInCrc();
			} else {
				end(ctx);
			}
		} else if (event.id.equals(hTE.IN_END) || event.id.equals(MyChannel.IN_EOF)) {
			if (state == END) return;
			if (exactLimit && 0 != readLimit) throw new EOFException("预期额外"+readLimit+"字节");
			if (state != IDENTITY) throw new EOFException(event.id);
			end(ctx);
		}
	}

	private void end(ChannelCtx ctx) throws IOException {
		if (state == END) return;
		ctx.postEvent(IN_END);
		state = END;
	}

	@Override
	@SuppressWarnings("fallthrough")
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		var buf = (DynByteBuf) msg;
		switch (state) {
			case DEFLATE_INIT -> {
				if (buf.readableBytes() < 2) return;
				count(ctx, buf);
				ctx = hDeflate.inflate(ctx, checkWrap(buf.readUnsignedShort(buf.rIndex)), false);
				state = DEFLATE;
				ctx.handler().channelRead(ctx, buf);
			}
			case GZIP_INIT -> {
				if (buf.readableBytes() < 10) return;
				int pos = buf.rIndex;
				try {
					checkGZHeader(buf);
				} catch (IndexOutOfBoundsException i) {
					buf.rIndex = pos;
					return;
				}
				readLimit -= (buf.rIndex - pos);
				count(ctx, buf);
				ctx = hDeflate.inflate(ctx, new Inflater(true), true);
				state = GZIP;
				// 如果数据够短，仅仅buf就包含了后续所有内容
				readLimit += 8;
				ctx.handler().channelRead(ctx, buf);
				readLimit -= 8;
			}
			case CHECKSUM -> {
				if (buf.readableBytes() < 8) return;
				count(ctx, buf);
				int crc32 = buf.readIntLE();
				int rcvByte = buf.readIntLE();
				if (crc32 != crc || rcvByte != i32rcv) {
					throw new IOException("gzip数据校验错误:"+
							("crc=0x"+Integer.toHexString(crc32)+"|0x"+Integer.toHexString(crc))+"\n" +
							("len="+i32rcv+"|"+rcvByte));
				}
				end(ctx);
			}
			case END -> ctx.channelRead(buf);
			default -> {
				long remain = readLimit-buf.readableBytes();
				if (remain <= 0) {
					int pos = buf.wIndex();
					buf.wIndex(buf.rIndex+(int)readLimit);
					ctx.channelRead(buf);
					buf.wIndex(pos);

					if (state == IDENTITY && exactLimit) end(ctx);
					if (remain < 0) ctx.postEvent(new Event(TOO_MANY_DATA, -remain));
				} else {
					ctx.channelRead(buf);
				}
				readLimit = remain;
			}
		}
	}

	private void count(ChannelCtx ctx, DynByteBuf buf) throws IOException {
		readLimit -= buf.readableBytes();
		if (readLimit < 0) ctx.postEvent(new Event(TOO_MANY_DATA, -readLimit));
	}

	/*
	 * File header flags.
	 */
	private final static int FTEXT = 1;     // Extra text
	private final static int FHCRC = 2;     // Header CRC
	private final static int FEXTRA = 4;    // Extra field
	private final static int FNAME = 8;     // File name
	private final static int FCOMMENT = 16; // File comment
	private static int checkGZHeader(DynByteBuf buf) throws IOException {
		int rPos = buf.rIndex;

		// Check header magic
		if (buf.readUShortLE() != GZIP_MAGIC) {
			throw new ZipException("Not in GZIP format");
		}
		// Check compression method
		if (buf.readUnsignedByte() != 8) {
			throw new ZipException("Unsupported compression method");
		}
		// Read flags
		int flg = buf.readUnsignedByte();
		// Skip MTIME, XFL, and OS fields
		buf.rIndex += 6;

		int n = 2 + 2 + 6;

		// extra
		if ((flg & FEXTRA) != 0) {
			int m = buf.readUShortLE();
			buf.rIndex += m;
			n += m + 2;
		}

		// file name
		if ((flg & FNAME) != 0) {
			do {
				n++;
			} while (buf.readByte() != 0);
		}

		// comment
		if ((flg & FCOMMENT) != 0) {
			do {
				n++;
			} while (buf.readByte() != 0);
		}

		// header CRC
		if ((flg & FHCRC) != 0) {
			int v = CRC32.crc32(buf, rPos, buf.rIndex - rPos)&0xFFFF;
			if (buf.readUShortLE() != v) throw new ZipException("Corrupt GZIP header");
			n += 2;
		}

		return n;
	}

	public static Inflater checkWrap(int firstTwoByte) {
		int flag0 = firstTwoByte >>> 8;

		// 检测Zlib Header
		boolean wrap = false;
		// CM
		if ((flag0 & 0xF) == 0x8) {
			// CINFO
			if ((flag0 >>> 4 & 0xF) <= 7) {
				// FCHECK
				if (0 == firstTwoByte % 31) {
					wrap = true;
				}
			}
		}
		return new Inflater(!wrap);
	}
}
