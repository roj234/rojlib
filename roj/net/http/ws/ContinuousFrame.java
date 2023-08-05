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

	public final byte data;
	int fragments;
	private DynByteBuf packet;
	long length;

	public int fragments() { return fragments; }
	public long length() { return length; }
	public DynByteBuf payload() { return packet; }

	public void append(ChannelCtx ctx, DynByteBuf b) {
		if (packet == null) packet = ctx.allocate(true, b.readableBytes());
		else if (packet.writableBytes()<b.readableBytes()) packet = BufferPool.expand(packet, b.readableBytes());

		packet.put(b);
		length += b.readableBytes();
		fragments++;
	}

	public void clear(ChannelCtx ctx) {
		if (packet != null) {
			BufferPool.reserve(packet);
			packet = null;
		}
	}

	@Override
	public String toString() {
		return "Fragments{" + "data=" + data + ", fragments=" + fragments + ", length=" + length + '}';
	}
}
