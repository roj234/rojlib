package roj.net.http.server;

import roj.collect.RingBuffer;
import roj.io.buf.BufferPool;
import roj.net.ChannelCtx;
import roj.net.http.Headers;
import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * StreamResponse removed since this is a non-blocking server
 * @author Roj234
 * @since 2021/2/16 11:21
 */
public class AsyncResponse implements Response {
	private final RingBuffer<DynByteBuf> packets = new RingBuffer<>(3);
	private boolean eof;

	public AsyncResponse() {}

	public boolean offer(DynByteBuf buf) {
		synchronized (packets) {
			if (eof) throw new IllegalStateException("eof");
			if (packets.remaining() == 0) return false;
			packets.addLast(BufferPool.localPool().allocate(true, buf.readableBytes(), 0).put(buf));
			return true;
		}
	}
	public boolean offerAndRelease(DynByteBuf buf) {
		synchronized (packets) {
			if (eof) throw new IllegalStateException("eof");
			return packets.offerLast(buf);
		}
	}
	public void setEof() { eof = true; }
	public boolean isEof() {return eof;}
	public int getPendingCount() { return packets.size(); }

	@Override
	public void prepare(ResponseHeader rh, Headers h) throws IOException {}

	public boolean send(ResponseWriter rh) throws IOException {
		DynByteBuf buf = packets.peekFirst();
		if (buf != null) {
			rh.write(buf);
			if (!buf.isReadable()) {
				synchronized (packets) {packets.removeFirst();}
				BufferPool.reserve(buf);
				if (packets.size() <= 1) hasSpaceCallback();
			}
		}
		return buf != null || !eof;
	}
	protected void hasSpaceCallback() {}

	@Override
	public void release(ChannelCtx ctx) throws IOException {
		eof = true;
		synchronized (packets) {
			for (DynByteBuf buf : packets)
				BufferPool.reserve(buf);
		}
		packets.clear();
	}
}