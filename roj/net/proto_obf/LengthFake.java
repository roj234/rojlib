package roj.net.proto_obf;

import roj.collect.RingBuffer;
import roj.crypt.MT19937;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.handler.PacketMerger;
import roj.net.ch.handler.VarintSplitter;
import roj.util.ArrayCache;
import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * 伪装数据包长度
 * @author Roj234
 * @since 2023/9/15 0015 22:04
 */
public class LengthFake extends PacketMerger implements ChannelHandler {
	final MT19937 rnd;
	final RingBuffer<DynByteBuf> buffers = new RingBuffer<>(5, 999);
	int delay;

	public LengthFake(MT19937 rnd) { this.rnd = rnd; }

	@Override
	public final void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf b = (DynByteBuf) msg;
		DynByteBuf tmp = ctx.allocate(true, 1024);
		try {
			while (b.isReadable()) {
				int len = _length(b);

				tmp.clear();
				if (tmp.capacity() < len+4) tmp = ctx.alloc().expand(tmp, len+4-tmp.capacity());
				tmp.putInt(0);

				if (len > b.readableBytes()) {
					int noiseLen = len - b.readableBytes();
					byte[] array = ArrayCache.getDefaultCache().getByteArray(noiseLen, false);
					rnd.nextBytes(array, 0, noiseLen);

					tmp = writePacket(ctx, tmp, b, b.readableBytes());
					tmp.put(array, 0, Math.min(noiseLen, tmp.writableBytes()));

					ArrayCache.getDefaultCache().putArray(array);
				} else {
					tmp = writePacket(ctx, tmp, b, len);
				}

				len = tmp.readableBytes()-4;
				int bl = VarintSplitter.getVarIntLength(len);
				tmp.wIndex(4-bl);tmp.putVUInt(len).rIndex = 4-bl;
				tmp.wIndex(len+4);

				if (delay == 0) {
					ctx.channelWrite(tmp);
					delay = _delay(b);
				} else {
					buffers.ringAddLast(ctx.allocate(true, tmp.readableBytes()).put(tmp));
				}
			}
		} finally {
			ctx.reserve(tmp);
		}
	}

	@Override
	public final void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf in = (DynByteBuf) msg;
		int pos = in.rIndex;
		int lim = in.wIndex();

		while (in.isReadable()) {
			int len = VarintSplitter.readVUInt(in, 3);

			if (len >= 0 && in.readableBytes() >= len) {
				in.wIndex(pos = in.rIndex + len);

				readPacket(ctx, in);

				if (in.capacity() > 0) {
					in.wIndex(lim);
					in.rIndex = pos;
				}
			} else {
				in.rIndex(pos);
				in.compact();
				return;
			}
		}
	}

	@Override
	public void channelTick(ChannelCtx ctx) throws Exception {
		if (delay != 0 && --delay == 0) {
			do {
				DynByteBuf buf = buffers.pollFirst();
				if (buf == null) break;

				try {
					ctx.channelWrite(buf);
				} finally {
					ctx.reserve(buf);
				}

				buf = buffers.peekFirst();
				if (buf == null) break;

				delay = _delay(buf);
			} while (delay == 0);
		}
	}

	@Override
	public void channelFlushing(ChannelCtx ctx) {
		boolean doWrite = true;
		while (!buffers.isEmpty()) {
			DynByteBuf buf = buffers.pollFirst();
			try {
				if (doWrite) ctx.channelWrite(buf);
			} catch (IOException e) {
				e.printStackTrace();
				doWrite = false;
			} finally {
				ctx.reserve(buf);
			}
		}
	}

	@Override
	public void handlerRemoved(ChannelCtx ctx) { channelFlushing(ctx); }

	void readPacket(ChannelCtx ctx, DynByteBuf in) throws IOException { ctx.channelRead(in); }
	DynByteBuf writePacket(ChannelCtx ctx, DynByteBuf out, DynByteBuf in, int len) throws IOException {
		out.put(in, len);
		in.rIndex += len;
		return out;
	}

	/** 随机长度 */
	int _length(DynByteBuf buf) {
		float p = rnd.nextFloat();
		if (buf.readableBytes() < p*50) return buf.readableBytes();
		return rnd.nextInt(buf.readableBytes()+1);
	}

	/** 随机延迟 */
	int _delay(DynByteBuf buf) {
		float p = rnd.nextFloat();
		if (p < 0.3f) return 0;
		if (p < 0.8f) return rnd.nextInt(30);
		return rnd.nextInt((int) (1+p/100f*buf.readableBytes()));
	}
}
