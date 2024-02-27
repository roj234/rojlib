package roj.net.http;

import roj.net.ch.ChannelCtx;
import roj.net.ch.Event;
import roj.net.handler.PacketMerger;
import roj.reflect.ReflectionUtils;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;

import static roj.text.TextUtil.digits;

/**
 * ChunkEncoding
 * @author Roj233
 * @since 2022/6/2 3:29
 */
public final class HChunk extends PacketMerger {
	public static final String EVENT_IN_END = "hChunk:inEnd", EVENT_CLOSE_OUT = "hChunk:closeOut";

	private static final int IN_ENABLE = 1, OUT_ENABLE = 2, IN_EOF = 4;

	private byte flag;

	private final ByteList tmp;

	private long inLen;

	public HChunk() {tmp = ByteList.allocate(16);}

	@Override
	public void channelRead(ChannelCtx ctx, Object o) throws IOException {
		if ((flag & IN_ENABLE) == 0) {
			ctx.channelRead(o);
			return;
		}

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
				if (buf.readableBytes() < 2) return;
				if (buf.readUnsignedShort() != 0x0D0A)
					throw new IllegalArgumentException("ChunkEncoding: 尾部无效");
				if ((flag & IN_EOF) != 0) {
					flag &= ~(IN_ENABLE|IN_EOF);
					ctx.postEvent(EVENT_IN_END);

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
			if (buf.getU(i) != 0x0D) {
				i+=1;
				continue;
			} else if (buf.getU(i+1) != 0x0A) {
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
		if ((flag & OUT_ENABLE) == 0) {
			ctx.channelWrite(o);
			return;
		}

		var in = (DynByteBuf) o;
		if (!in.isReadable()) flag &= ~OUT_ENABLE;

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

		// TODo 测试Lavac的Tailrec
		//  另外把我之前做的上传UI找到，和ChunkUpload搞一搞
	}

	private int writeLength(int i, long addr) {
		long pos = 7;

		while (i > 15) {
			ReflectionUtils.u.putByte(addr + pos--, digits[i&0xF]);
			i >>>= 4;
		}
		ReflectionUtils.u.putByte(addr + pos, digits[i&0xF]);

		return (int) pos;
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id.equals(EVENT_CLOSE_OUT)) {
			if ((flag&OUT_ENABLE) != 0) {
				flag &= ~OUT_ENABLE;

				try (var buf = ctx.alloc().allocate(true, 5, 0)) {
					buf.put('0').putInt(0x0D0A0D0A);
					ctx.channelWrite(buf);
				}
			}
		}
	}

	public void reset() {flag = 0;}
	public void enableIn() {flag |= IN_ENABLE;inLen = -1;}
	public void enableOut() {flag |= OUT_ENABLE;}
}