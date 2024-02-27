package roj.net.http.ws;

import roj.io.buf.BufferPool;
import roj.net.ch.ChannelCtx;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/11/11 0011 1:30
 */
final class ContinuousFrame {
	ContinuousFrame(int data) { this.data = (byte) data; }

	final byte data;
	int fragments;
	private DynByteBuf packet;
	long length;

	DynByteBuf payload() { return packet; }

	void append(ChannelCtx ctx, DynByteBuf b) {
		if (packet == null) packet = ctx.allocate(true, b.readableBytes());
		else if (packet.writableBytes()<b.readableBytes()) packet = ctx.alloc().expand(packet, b.readableBytes());

		packet.put(b);
		length += b.readableBytes();
		fragments++;
	}

	void clear() {
		if (packet != null) {
			BufferPool.reserve(packet);
			packet = null;
		}
	}
}