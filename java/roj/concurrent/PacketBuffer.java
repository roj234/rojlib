package roj.concurrent;

import org.jetbrains.annotations.Nullable;
import roj.io.buf.BufferPool;
import roj.reflect.ReflectionUtils;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.concurrent.locks.LockSupport;

import static roj.reflect.ReflectionUtils.u;

/**
 * 一个适合【多线程写入，单线程读取】的无界FIFO队列
 * @author Roj234
 * @since 2023/5/17 0017 18:48
 */
public final class PacketBuffer {
	private static final class Entry {
		volatile Entry next;
		volatile Thread waiter;
		volatile DynByteBuf buffer;
	}

	private static final long
		u_head = ReflectionUtils.fieldOffset(PacketBuffer.class, "head"),
		u_recycle = ReflectionUtils.fieldOffset(PacketBuffer.class, "recycle"),
		u_recycleSize = ReflectionUtils.fieldOffset(PacketBuffer.class, "recycleSize"),
		u_entryNext = ReflectionUtils.fieldOffset(Entry.class, "next"),
		u_entryWaiter = ReflectionUtils.fieldOffset(Entry.class, "waiter");

	private volatile Entry head, recycle;
	private volatile int recycleSize;

	private Entry rHead, rTail;

	private final int max;

	public PacketBuffer(int maxUnused) {
		this.max = maxUnused;
	}

	public void offer(DynByteBuf b) {doOffer(b, false);}
	public void offerSync(DynByteBuf b) {doOffer(b, true);}
	private boolean doOffer(DynByteBuf b, boolean wait) {
		Entry entry;
		while (true) {
			entry = recycle;
			if (entry == null) {
				entry = new Entry();
				break;
			}

			Entry next = entry.next;
			// 似乎有些bug，可能会扔掉一些对象
			if (u.compareAndSwapObject(this, u_recycle, entry, next)) {
				u.getAndAddInt(this, u_recycleSize, -1);

				entry.next = null;
				break;
			}
		}

		// if stable, will not use this!
		entry.buffer = BufferPool.buffer(true, b.readableBytes()).put(b);
		Thread waiter = wait ? Thread.currentThread() : null;
		entry.waiter = waiter;

		// need this ?
		u.storeFence();

		entry.next = (Entry) u.getAndSetObject(this, u_head, entry);

		if (wait) while (entry.waiter == waiter) LockSupport.park(this);

		return true;
	}

	@Nullable
	public DynByteBuf take(DynByteBuf b) {return removeWith(b, true);}
	public boolean mayTake(DynByteBuf b) { return removeWith(b, false) != null; }

	private DynByteBuf removeWith(DynByteBuf buf, boolean must) {
		pollReverse();

		Entry entry = rHead;
		if (entry == null) return null;

		DynByteBuf data = entry.buffer;
		assert data != null;

		try {
			if (buf.writableBytes() < data.readableBytes()) {
				if (!must) return null;
				buf = new ByteList(data.readableBytes());
			}

			buf.put(data);
		} finally {
			assert entry.buffer == data;
			entry.buffer = null;
			BufferPool.reserve(data);
		}

		LockSupport.unpark((Thread) u.getAndSetObject(entry, u_entryWaiter, null));

		if (entry.next == null) {
			assert entry == rTail;
			rTail = null;
		}
		rHead = (Entry) u.getAndSetObject(entry, u_entryNext, null);

		if (recycleSize < max) {
			entry.next = (Entry) u.getAndSetObject(this, u_recycle, entry);
			u.getAndAddInt(this, u_recycleSize, 1);
		}

		return buf;
	}
	private void pollReverse() {
		Entry h = (Entry) u.getAndSetObject(this, u_head, null);
		if (h == null) return;

		Entry hold = h;
		Entry rev = null;

		while (h != null) {
			Entry next = (Entry) u.getAndSetObject(h, u_entryNext, rev);

			rev = h;

			h = next;
		}

		if (rHead == null) {
			rHead = rev;
			rTail = hold;
		} else {
			rTail.next = rev;
			rTail = rev;
		}
	}

	private boolean release() {
		pollReverse();

		Entry entry = rHead;
		if (entry == null) return false;

		BufferPool.reserve(entry.buffer);

		LockSupport.unpark((Thread) u.getAndSetObject(entry, u_entryWaiter, null));

		if (entry.next == null) {
			assert entry == rTail;
			rTail = null;
		}
		rHead = (Entry) u.getAndSetObject(entry, u_entryNext, null);

		return true;
	}

	public boolean isEmpty() {return head == null && rHead == null;}
	public void clear() {while (release());}
}