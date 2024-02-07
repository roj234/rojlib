package roj.concurrent;

import roj.io.buf.BufferPool;
import roj.reflect.ReflectionUtils;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2023/5/17 0017 18:48
 */
public class PacketBuffer {
	protected static class Entry {
		volatile Entry next;
		protected DynByteBuf buffer;
	}

	private static final long
		u_size = ReflectionUtils.fieldOffset(PacketBuffer.class, "size"),
		u_tail = ReflectionUtils.fieldOffset(PacketBuffer.class, "tail");

	volatile Entry head, tail;
	volatile int size;

	private final int max;

	public PacketBuffer(int max) {
		this.head = this.tail = createEntry();
		this.max = max;
	}

	protected Entry createEntry() { return new Entry(); }
	protected void cacheEntry(Entry entry) {}

	public void offer(DynByteBuf b) {
		Entry entry = createEntry();
		entry.buffer = BufferPool.buffer(true, b.readableBytes()).put(b);
		b.rIndex = b.wIndex();
		doOffer(entry, true);
	}
	private boolean doOffer(Entry entry, boolean wait) {
		int i = 0;
		while (true) {
			Entry tail = this.tail;
			if (size < max && u.compareAndSwapObject(this, u_tail, tail, entry)) {
				while (true) {
					int s = size;
					if (u.compareAndSwapInt(this, u_size, s, s+1)) break;
				}

				tail.next = entry;
				break;
			}

			if (size >= max && (++i & 15) == 0) {
				if (!wait) return false;

				try {
					synchronized (this) {
						if (size >= max) wait();
					}
				} catch (InterruptedException e) {
					throw new IllegalStateException("wait cancelled due to interrupt");
				}
			}
		}

		return true;
	}

	public DynByteBuf take(DynByteBuf b) {
		DynByteBuf r = removeWith(b, true);
		return r == null ? b : r;
	}
	public boolean mayTake(DynByteBuf b) { return removeWith(b, false) == b; }

	private DynByteBuf removeWith(DynByteBuf buf, boolean must) {
		Entry entry = poll(must?Integer.MAX_VALUE:buf.writableBytes());
		if (entry == null) return null;

		DynByteBuf data = entry.buffer;
		assert data != null;

		try {
			if (data.readableBytes() > buf.writableBytes()) buf = new ByteList(buf.readableBytes()).put(buf);
			else buf.put(data);
		} finally {
			BufferPool.reserve(entry.buffer);
			entry.buffer = null;
		}

		return buf;
	}
	private Entry poll(int len) {
		Entry prev = head, entry = prev.next;

		if (entry == null || entry.buffer.readableBytes() > len) return null;

		prev.next = null;
		head = entry;

		while (true) {
			int s = size;
			if (u.compareAndSwapInt(this, u_size, s, s-1)) break;
		}

		return entry;
	}

	public boolean isEmpty() { return size == 0; }
	public int size() { return size; }
	public int remaining() { return max - size; }

	public void clear() {
		Entry prev = head, task = prev.next;

		while (task != null) {
			prev = task;
			task = task.next;
		}

		task = head;
		while (task != prev) {
			Entry t = task;
			task = task.next;
			t.next = null;
		}

		head = prev;
	}
}
