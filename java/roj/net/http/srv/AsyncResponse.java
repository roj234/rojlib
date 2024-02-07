package roj.net.http.srv;

import roj.collect.RingBuffer;
import roj.io.buf.BufferPool;
import roj.net.ch.ChannelCtx;
import roj.net.http.Headers;
import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * StreamResponse removed since this is a non-blocking server
 * @author Roj234
 * @since 2021/2/16 11:21
 */
public class AsyncResponse implements Response {
	private final RingBuffer<DynByteBuf> packets = new RingBuffer<>(10, 100);
	private ChannelCtx ctx;
	private boolean eof;

	public AsyncResponse() {}

	public boolean asyncOffer(DynByteBuf buf) {
		synchronized (packets) {
			if (eof) throw new IllegalStateException("eof");
			if (packets.remaining() == 0) return false;
			packets.ringAddLast(ctx.channel().alloc().allocate(true, buf.readableBytes(), 0).put(buf));
			return true;
		}
	}
	public boolean asyncOfferAsyncRelease(DynByteBuf buf) {
		if (!BufferPool.isPooled(buf)) throw new IllegalArgumentException("buffer is not pooled");
		synchronized (packets) {
			if (eof) throw new IllegalStateException("eof");
			if (packets.remaining() == 0) return false;
			packets.ringAddLast(buf);
			return true;
		}
	}
	public void asyncSetEof() { eof = true; }

	@Override
	public void prepare(ResponseHeader srv, Headers h) throws IOException { ctx = srv.ch(); }

	public boolean send(ResponseWriter rh) throws IOException {
		DynByteBuf buf = packets.peekFirst();
		if (buf != null) {
			rh.write(buf);
			if (!buf.isReadable()) {
				packets.removeFirst();
				BufferPool.reserve(buf);
			}
		}
		return buf != null || !eof;
	}

	@Override
	public void release(ChannelCtx ctx) throws IOException {
		eof = true;
		synchronized (packets) {
			for (DynByteBuf packet : packets)
				BufferPool.reserve(packet);
		}
		packets.clear();
	}
}