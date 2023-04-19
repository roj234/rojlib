package roj.net.http.ws;

import roj.net.ch.ChannelCtx;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/11/11 0011 1:30
 */
final class ContinuousFrame {
	ContinuousFrame(int data) {
		this.data = (byte) data;
	}

	public final byte data;
	int fragments;
	private DynByteBuf payload;
	long length;

	public int fragments() {
		return fragments;
	}

	public long length() {
		return length;
	}

	public void append(ChannelCtx ctx, DynByteBuf b) {
		payload = payload == null ? ctx.allocate(true, b.readableBytes()) : ctx.alloc().expand(payload, b.readableBytes());

		payload.put(b);
		length += b.readableBytes();
		fragments++;
	}

	public DynByteBuf payload(ChannelCtx ctx) {
		return payload;
	}

	public void clear(ChannelCtx ctx) {
		if (payload != null) {
			ctx.reserve(payload);
			payload = null;
		}
	}

	@Override
	public String toString() {
		return "Fragments{" + "data=" + data + ", fragments=" + fragments + ", length=" + length + '}';
	}
}
