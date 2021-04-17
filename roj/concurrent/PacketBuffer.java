package roj.concurrent;

import roj.io.buf.BufferPool;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

/**
 * @author Roj233
 * @since 2021/12/30 12:31
 */
public final class PacketBuffer extends ManyPutOneGet<DynByteBuf> {
	static final class Buf extends Entry<DynByteBuf> {
		BufferPool pool;
	}

	public PacketBuffer(int max) { super(max); }

	@Override
	protected Buf createEntry() { return new Buf(); }

	public void offer(DynByteBuf b) {
		Buf entry = new Buf();
		entry.pool = BufferPool.localPool();
		entry.ref = entry.pool.buffer(true, b.readableBytes()).put(b);
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
		Buf entry = (Buf) poll(must ? Helpers.alwaysTrue() : e -> e.ref.readableBytes() <= finalBuf.writableBytes());
		if (entry == null) return null;

		DynByteBuf data = entry.ref;
		if (data == null) throw new InternalError();

		try {
			if (data.readableBytes() > buf.writableBytes()) buf = new ByteList().put(buf);
			else buf.put(data);
		} finally {
			entry.pool.reserve(entry.ref);
			entry.pool = null;
			entry.ref = null;
		}

		return buf;
	}
}
