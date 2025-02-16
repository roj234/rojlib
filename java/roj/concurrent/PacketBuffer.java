package roj.concurrent;

import org.jetbrains.annotations.Nullable;
import roj.io.buf.BufferPool;
import roj.reflect.ReflectionUtils;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.concurrent.locks.LockSupport;

import static roj.reflect.Unaligned.U;

/**
 * 一个适合【多线程写入，单线程读取】的无界FIFO队列
 * @author Roj234
 * @since 2023/5/17 0017 18:48
 */
public final class PacketBuffer {
	static class Entry extends FIFOQueue.Node {
		volatile DynByteBuf buffer;
		volatile Thread waiter;
	}

	final FIFOQueue<Entry> queue = new FIFOQueue<>(), recycle = new FIFOQueue<>();

	private static final long SIZE_OFFSET = ReflectionUtils.fieldOffset(PacketBuffer.class, "recycleSize");
	private volatile int recycleSize;
	private final int max;

	public PacketBuffer(int maxUnused) {this.max = maxUnused;}

	public void offer(DynByteBuf b) {doOffer(b, false);}
	public void offerSync(DynByteBuf b) {doOffer(b, true);}
	private boolean doOffer(DynByteBuf b, boolean wait) {
		var entry = recycle.removeFirst();
		if (entry == null) entry = new Entry();

		entry.buffer = BufferPool.buffer(true, b.readableBytes()).put(b);
		Thread waiter = wait ? Thread.currentThread() : null;
		entry.waiter = waiter;

		// really need this ?
		U.storeFence();

		queue.addLast(entry);

		if (wait) while (entry.waiter == waiter) LockSupport.park(this);

		return true;
	}

	@Nullable
	public DynByteBuf take(DynByteBuf b) {return removeWith(b, true);}
	public boolean mayTake(DynByteBuf b) { return removeWith(b, false) != null; }

	private DynByteBuf removeWith(DynByteBuf buf, boolean must) {
		Entry entry;
		while (true) {
			entry = queue.peek();
			if (entry == null) return null;

			var data = entry.buffer;
			if (data == null) continue;
			if (buf.writableBytes() < data.readableBytes() && !must) return null;

			if (queue.removeIf(entry) != null) break;
		}

		var data = entry.buffer;
		entry.buffer = null;
		assert data != null;

		try {
			if (buf.writableBytes() < data.readableBytes()) {
				buf = new ByteList(data.toByteArray());
			} else {
				buf.put(data);
			}
		} finally {
			BufferPool.reserve(data);
		}

		var thread = entry.waiter;
		entry.waiter = null;
		LockSupport.unpark(thread);

		U.storeFence();

		if (recycleSize < max) {
			recycle.addLast(entry);
			U.getAndAddInt(this, SIZE_OFFSET, 1);
		}

		return buf;
	}

	public boolean isEmpty() {return queue.peek() == null;}

	public void clear() {
		while (true) {
			var entry = queue.removeFirst();
			if (entry == null) break;

			var data = entry.buffer;
			entry.buffer = null;
			assert data != null;
			BufferPool.reserve(data);

			var thread = entry.waiter;
			entry.waiter = null;
			LockSupport.unpark(thread);
		}
	}
}