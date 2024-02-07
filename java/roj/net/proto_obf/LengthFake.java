package roj.net.proto_obf;

import roj.collect.RingBuffer;
import roj.io.buf.BufferPool;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.handler.PacketMerger;
import roj.net.ch.handler.VarintSplitter;
import roj.util.ArrayCache;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.Random;

/**
 * 伪装数据包长度
 * @author Roj234
 * @since 2023/9/15 0015 22:04
 */
public class LengthFake extends PacketMerger implements ChannelHandler {
	final Random rnd;
	final RingBuffer<DynByteBuf> buffers = new RingBuffer<>(5, 999);
	int delay;

	public LengthFake(Random rnd) { this.rnd = rnd; }

	@Override
	public final void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf b = (DynByteBuf) msg;
		DynByteBuf tmp = ctx.allocate(true, 1024);
		try {
			while (b.isReadable()) {
				int len = _length(b.readableBytes());

				tmp.clear();
				if (tmp.capacity() < len+4) tmp = BufferPool.expand(tmp, len+4-tmp.capacity());
				tmp.putInt(0);

				if (len > b.readableBytes()) {
					int noiseLen = len - b.readableBytes();
					byte[] array = ArrayCache.getByteArray(noiseLen, false);
					nextBytes(rnd, array, 0, noiseLen);

					tmp = writePacket(ctx, tmp, b, b.readableBytes());
					tmp.put(array, 0, Math.min(noiseLen, tmp.writableBytes()));

					ArrayCache.putArray(array);
				} else {
					tmp = writePacket(ctx, tmp, b, len);
				}

				len = tmp.readableBytes()-4;
				int bl = VarintSplitter.getVarIntLength(len);
				tmp.wIndex(tmp.rIndex = 4-bl);
				tmp.putVUInt(len).wIndex(len+4);

				if (delay == 0) {
					ctx.channelWrite(tmp);
					delay = _delay(len);
				} else {
					buffers.ringAddLast(ctx.allocate(true, tmp.readableBytes()).put(tmp));
				}
			}
		} finally {
			BufferPool.reserve(tmp);
		}
	}

	static void nextBytes(Random r, byte[] bytes, int i, int len) {
		while (i < len) {
			for (int rnd = r.nextInt(),
				 n = Math.min(len - i, Integer.SIZE/Byte.SIZE);
				 n-- > 0; rnd >>= Byte.SIZE)
				bytes[i++] = (byte)rnd;
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
					BufferPool.reserve(buf);
				}

				buf = buffers.peekFirst();
				if (buf == null) break;

				delay = _delay(buf.readableBytes());
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
				BufferPool.reserve(buf);
			}
		}
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException { channelFlushing(ctx); }
	@Override
	public void handlerRemoved(ChannelCtx ctx) { channelFlushing(ctx); }

	void readPacket(ChannelCtx ctx, DynByteBuf in) throws IOException { ctx.channelRead(in); }
	DynByteBuf writePacket(ChannelCtx ctx, DynByteBuf out, DynByteBuf in, int len) throws IOException {
		out.put(in, len);
		in.rIndex += len;
		return out;
	}

	/** 随机长度 */
	int _length(int buf) {
		float p = rnd.nextFloat();
		if (buf < p*50) return buf;
		return rnd.nextInt(buf+1);
	}

	/** 随机延迟 */
	int _delay(int buf) {
		float p = rnd.nextFloat();
		if (p < 0.3f) return 0;
		if (p < 0.8f) return rnd.nextInt(30);
		return rnd.nextInt(1+(int) (p*buf));
	}
}
