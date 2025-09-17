package roj.http;

import roj.net.ChannelCtx;
import roj.net.Event;
import roj.net.MyChannel;
import roj.net.handler.PacketMerger;
import roj.reflect.Unsafe;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.EOFException;
import java.io.IOException;

import static roj.text.TextUtil.DIGITS;

/**
 * Transfer-Encoding: Chunked
 * @author Roj233
 * @since 2022/6/2 3:29
 */
public final class hTE extends PacketMerger {
	public static final String ANCHOR = "http:te_handler";
	public static final String IN_END = "hte:inEnd", OUT_FINISH = "hte:outFin";

	private static final int IN = 1, OUT = 2, IN_EOF = 4;
	private byte flag;

	private final ByteList tmp;

	private long inLen;
	private Headers trailers;

	private hTE() {tmp = ByteList.allocate(16);}
	public static ChannelCtx apply(ChannelCtx ctx, Headers response, int dir) {
		if (response != null) {
			String transferEncoding = response.get("transfer-encoding", "identity");
			if ("identity".equalsIgnoreCase(transferEncoding)) dir = 0;
			else if (!"chunked".equalsIgnoreCase(transferEncoding)) throw new UnsupportedOperationException("不支持的传输编码:"+transferEncoding);
		}

		hTE hte;
		var exist = ctx.channel().handler(ANCHOR);

		if (exist == null) {
			if (dir == 0) return ctx;

			var anchor = ctx.channel().handler(hCE.ANCHOR);
			if (anchor != null) ctx = anchor;

			ctx.channel().addBefore(ctx, ANCHOR, hte = new hTE());
			exist = ctx.channel().handler(ANCHOR);
		} else {
			hte = (hTE) exist.handler();
			if (dir == 0) {hte.reset();return ctx;}
		}

		if (dir == -1) {
			hte.enableIn();
			if (response != null && response.get("trailer") != null)
				hte.trailers = new Headers();
		}
		else hte.enableOut();
		return exist;
	}

	public static void reset(ChannelCtx ctx) {
		var exist = ctx.channel().handler(ANCHOR);
		if (exist != null) ((hTE) exist.handler()).reset();
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object o) throws IOException {
		if ((flag & IN) == 0) {ctx.channelRead(o);return;}

		DynByteBuf buf = (DynByteBuf) o;
		while (buf.isReadable()) {
			if (inLen < 0) {
				if (!findHead(buf)) return;
			} else if (inLen > 0) {
				int w = buf.wIndex();

				int actualR = (int) Math.min(inLen, buf.readableBytes());

				buf.wIndex(buf.rIndex+actualR);
				try {
					mergedRead(ctx, buf);
				} finally {
					buf.wIndex(w);
				}

				inLen -= actualR;
			} else {
				if (trailers == null) {
					if (buf.readableBytes() < 2) return;
					if (buf.readUnsignedShort() != 0x0D0A)
						throw new IllegalArgumentException("ChunkEncoding: 尾部无效");
				} else {
					if (!Headers.parseHeader(trailers, buf)) return;
				}
				if ((flag&IN_EOF) != 0) {
					flag &= ~(IN|IN_EOF);
					ctx.postEvent(IN_END);

					mergedRead(ctx, buf);
					return;
				}
				inLen = -1;
			}
		}
	}

	private boolean findHead(DynByteBuf buf) {
		int i = buf.rIndex;
		int prevI = i;
		while (i < buf.wIndex()) {
			if (buf.getUnsignedByte(i) != 0x0D) {
				i+=1;
				continue;
			} else if (buf.getUnsignedByte(i+1) != 0x0A) {
				i+=2;
				continue;
			}

			int len = i - prevI;
			tmp.clear();
			tmp.put(buf, len);
			buf.rIndex += len;

			try {
				inLen = TextUtil.parseInt(tmp, 1);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("ChunkEncoding: 长度无效: "+tmp.dump());
			}

			buf.rIndex = i+2;
			if (inLen == 0) flag |= IN_EOF;

			return true;
		}

		// maybe missing
		if (i-prevI<18) return false;

		throw new IllegalArgumentException("ChunkEncoding: 无法找到头部: "+buf.dump());
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object o) throws IOException {
		if ((flag & OUT) == 0) {
			ctx.channelWrite(o);
			return;
		}

		var in = (DynByteBuf) o;
		if (!in.isReadable()) flag &= ~OUT;

		try (var lenBuf = ctx.alloc().allocate(true, 10, 0)) {
			// header
			lenBuf.rIndex = writeLength(in.readableBytes(), lenBuf.address());
			lenBuf.wIndex(8);
			ctx.channelWrite(lenBuf.putShort(0x0D0A));

			// content
			ctx.channelWrite(in);

			// trailer
			lenBuf.rIndex = 8;
			ctx.channelWrite(lenBuf);
		}
	}

	private int writeLength(int i, long addr) {
		long pos = 7;

		while (i > 15) {
			Unsafe.U.putByte(addr + pos--, DIGITS[i&0xF]);
			i >>>= 4;
		}
		Unsafe.U.putByte(addr + pos, DIGITS[i&0xF]);

		return (int) pos;
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id.equals(MyChannel.IN_EOF) && (flag&(IN|IN_EOF)) == IN) throw new EOFException(event.id);

		if (event.id.equals(OUT_FINISH)) {
			if ((flag&OUT) != 0) {
				flag &= ~OUT;

				try (var buf = ctx.alloc().allocate(true, 5, 0)) {
					buf.put('0').putInt(0x0D0A0D0A);
					ctx.channelWrite(buf);
				}
			}
		}
	}

	public void reset() {flag = 0;trailers = null;}
	public void enableIn() {flag |= IN;inLen = -1;}
	public void enableOut() {flag |= OUT;}
	public Headers getTrailers() {return trailers;}
}