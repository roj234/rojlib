package roj.net.http;

import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.Event;
import roj.net.ch.handler.PacketMerger;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Identifier;

import java.io.IOException;

import static roj.text.TextUtil.digits;

/**
 * @author Roj233
 * @since 2022/6/2 3:29
 */
public class ChunkSplitter extends PacketMerger implements ChannelHandler {
	public static final Identifier CHUNK_IN_EOF = Identifier.of("cs", "cin");
	public static final Identifier CHUNK_OUT_EOF = Identifier.of("cs", "cout");

	private static final int IN_ENABLE = 1, OUT_ENABLE = 2, IN_EOF = 4;

	private byte flag;

	private final byte[] hex;
	private final ByteList tmp;

	private long inLen;

	public ChunkSplitter() {
		this(0);
	}

	public ChunkSplitter(int flag) {
		super();

		tmp = ByteList.allocate(18);
		hex = tmp.list;

		this.flag = (byte) flag;
	}

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
					ctx.postEvent(CHUNK_IN_EOF);

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

			buf.read(hex, 0, i-prevI);

			try {
				tmp.rIndex = 0;
				tmp.wIndex(i-prevI);

				inLen = TextUtil.parseInt(tmp, 16);
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

		DynByteBuf in = (DynByteBuf) o;
		if (!in.isReadable()) flag &= ~OUT_ENABLE;

		tmp.rIndex = writeLength(in.readableBytes(), hex);
		tmp.wIndex(8);
		// header
		ctx.channelWrite(tmp.putShort(0x0D0A));
		// content
		ctx.channelWrite(in);
		// trailer
		tmp.clear();tmp.putShort(0x0D0A);
		ctx.channelWrite(tmp);
	}

	public static int writeLength(int i, byte[] buf) {
		int pos = 7;

		while (i > 15) {
			buf[pos--] = digits[i&15];
			i >>>= 4;
		}
		buf[pos] = digits[i&15];

		return pos;
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id == CHUNK_OUT_EOF) {
			if ((flag&OUT_ENABLE) != 0) {
				flag &= ~OUT_ENABLE;

				tmp.clear();
				tmp.put((byte) '0').putInt(0x0D0A0D0A);
				ctx.channelWrite(tmp);
			}
		}
	}

	public void reset() {
		flag = 0;
	}

	public void enableIn() {
		flag |= IN_ENABLE;
		inLen = -1;
	}

	public void enableOut() {
		flag |= OUT_ENABLE;
	}
}
