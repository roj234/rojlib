package roj.concurrent;

import org.jetbrains.annotations.Nullable;
import roj.io.BufferPool;
import roj.reflect.Unaligned;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.concurrent.locks.LockSupport;

import static roj.reflect.Unaligned.U;

/**
 * 一个适合【多线程写入，单线程读取】的无界FIFO队列
 * 多线程读取现在会出bug……除非把recycleSize设为0
 * @author Roj234
 * @since 2023/5/17 18:48
 */
public final class PacketBuffer extends ReuseFIFOQueue<PacketBuffer.Entry> {
	static final class Entry extends ReuseFIFOQueue.Node {
		volatile DynByteBuf buffer;
		volatile Thread waiter;
	}

	private static final long SIZE_OFFSET = Unaligned.fieldOffset(PacketBuffer.class, "recycleSize");
	private volatile int recycleSize;

	public PacketBuffer() {recycleSize = 4;}
	public PacketBuffer(int maxUnused) {this.recycleSize = maxUnused;}

	@Override
	protected void recycle(Node node) {
		if (node instanceof Entry && recycleSize > 0) {
			U.getAndAddInt(this, SIZE_OFFSET, -1);
			node.next = (Node)U.getAndSetReference(this, RECYCLE_OFFSET, node);
		}
	}
	private Entry retain() {
		while (true) {
			var bin = recycle;
			if (bin != null) {
				if (U.compareAndSetReference(this, RECYCLE_OFFSET, bin, bin.next)) {
					U.getAndAddInt(this, SIZE_OFFSET, 1);
					return (Entry) bin;
				}
			} else {
				return null;
			}
		}
	}

	public void offer(DynByteBuf b) {doOffer(b, false);}
	public void offerSync(DynByteBuf b) {doOffer(b, true);}
	private boolean doOffer(DynByteBuf b, boolean wait) {
		var entry = retain();
		if (entry == null) entry = new Entry();

		entry.buffer = BufferPool.buffer(true, b.readableBytes()).put(b);
		Thread waiter = wait ? Thread.currentThread() : null;
		entry.waiter = waiter;

		U.storeFence();

		addLast(entry);

		if (wait) while (entry.waiter == waiter) LockSupport.park(this);

		return true;
	}

	@Nullable
	public DynByteBuf take(DynByteBuf b) {return removeWith(b, true);}
	public boolean mayTake(DynByteBuf b) { return removeWith(b, false) != null; }

	private DynByteBuf removeWith(DynByteBuf buf, boolean must) {
		Entry entry;
		while (true) {
			entry = peek();
			if (entry == null) return null;

			var data = entry.buffer;
			if (data == null) continue;
			if (buf.writableBytes() < data.readableBytes() && !must) return null;

			if (removeIf(entry) != null) break;
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

		return buf;
	}

	public boolean isEmpty() {return peek() == null;}

	public void clear() {
		while (true) {
			var entry = removeFirst();
			if (entry == null) break;

			var data = entry.buffer;
			entry.buffer = null;
			assert data != null;
			BufferPool.reserve(data);

			var thread = entry.waiter;
			entry.waiter = null;
			LockSupport.unpark(thread);
		}

		recycle = null;
	}
}