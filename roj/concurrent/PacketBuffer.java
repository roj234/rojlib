package roj.concurrent;

import roj.RequireUpgrade;
import roj.io.buf.BufferPool;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

/**
 * @author Roj233
 * @since 2021/12/30 12:31
 */
@RequireUpgrade
public final class PacketBuffer extends ManyPutOneGet<DynByteBuf> {
	public PacketBuffer(int max) { super(max); }

	public void offer(DynByteBuf b) {
		Entry<DynByteBuf> entry = createEntry();
		entry.ref = BufferPool.buffer(true, b.readableBytes()).put(b);
		b.rIndex = b.wIndex();

		offer(entry, true);
	}

	public DynByteBuf take(DynByteBuf b) {
		DynByteBuf r = removeWith(b, true);
		return r == null ? b : r;
	}
	public boolean mayTake(DynByteBuf b) { return removeWith(b, false) == b; }

	private DynByteBuf removeWith(DynByteBuf buf, boolean must) {
		DynByteBuf finalBuf = buf;
		Entry<DynByteBuf> entry = poll(must ? Helpers.alwaysTrue() : e -> e.ref.readableBytes() <= finalBuf.writableBytes());
		if (entry == null) return null;

		DynByteBuf data = entry.ref;
		if (data == null) throw new InternalError();

		try {
			if (data.readableBytes() > buf.writableBytes()) buf = new ByteList().put(buf);
			else buf.put(data);
		} finally {
			BufferPool.reserve(entry.ref);
			entry.ref = null;
		}

		return buf;
	}
}
