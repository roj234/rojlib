package roj.concurrent;

import roj.io.buf.BufferPool;
import roj.util.ByteList;
import roj.util.DynByteBuf;

/**
 * @author Roj233
 * @since 2021/12/30 12:31
 */
public final class PacketBuffer extends CASRingBuffer<DynByteBuf> {
	static final class Buf {
		DynByteBuf buffer;
		BufferPool pool;
	}

	public void offer(DynByteBuf b) {
		if (isFull()) throw new IllegalArgumentException("PacketBuffer is full");

		DynByteBuf prev = ringAddLast(b);
		if (prev != null) throw new IllegalArgumentException("PacketBuffer is full");
	}

	public DynByteBuf poll() {
		return removeWith(null);
	}

	public DynByteBuf take(DynByteBuf b) {
		return removeWith(b);
	}

	private DynByteBuf removeWith(DynByteBuf buf) {
		DynByteBuf v;

		int head = readLock(HEAD_OFF);
		Object[] array = this.array;

		try {
			v = remove(array, head, buf);
			if (v == null) isEmpty = true;
		} finally {
			lock.releaseShared(head);
		}

		return v;
	}

	public PacketBuffer(int max) {
		super(max);
	}
	public PacketBuffer(int cap, int max) {
		super(cap, max);
	}

	protected DynByteBuf insert(Object[] array, int i, DynByteBuf e) {
		Buf buf = (Buf) array[i];
		DynByteBuf v = null;
		if (buf == null) {
			array[i] = buf = new Buf();
		} else if (buf.buffer != null) {
			v = ByteList.allocate(buf.buffer.readableBytes()).put(buf.buffer);
			buf.pool.reserve(buf.buffer);
		}

		buf.pool = BufferPool.localPool();
		buf.buffer = buf.pool.buffer(e.readableBytes()).put(e);

		return v;
	}

	private DynByteBuf remove(Object[] array, int i, DynByteBuf b) {
		Buf buf = (Buf) array[i];
		if (buf != null && buf.buffer != null) {
			if (b == null || b.writableBytes() < buf.buffer.readableBytes()) {
				b = ByteList.allocate(buf.buffer.readableBytes());
			}

			DynByteBuf v = b.put(buf.buffer);
			buf.pool.reserve(buf.buffer);
			buf.buffer = null;
			return v;
		}

		return null;
	}
}
