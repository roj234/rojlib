package roj.io.buf;

import roj.collect.SimpleList;
import roj.util.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Roj233
 * @since 2022/6/1 7:06
 */
public class BufferPool {
	private static final ThreadLocal<BufferPool> DEFAULT = ThreadLocal.withInitial(() -> {
		PagedBPool bp1 = new PagedBPool(524288);
		SimpleBPool bp2 = new SimpleBPool(512, 524288, 16);
		return new BufferPool(bp1,bp2);
	});
	public static BufferPool localPool() { return DEFAULT.get(); }

	private final class PooledDirectBuf extends DirectByteList.Slice implements PooledBuffer {
		private volatile BPool pool;
		private int meta;
		@Override
		public int getMetadata() { return meta; }
		@Override
		public void setMetadata(int m) { meta = m; }
		@Override
		public BPool pool() { return pool; }
		@Override
		public void pool(BPool pool) { this.pool = pool; }
		@Override
		public void close() { if (pool != null) BufferPool.this.reserve(this); }
		@Override
		public void release() { set(null,0L,0); }
	}
	private final class PooledHeapBuf extends ByteList.Slice implements PooledBuffer {
		private volatile BPool pool;
		private int meta;
		@Override
		public int getMetadata() { return meta; }
		@Override
		public void setMetadata(int m) { meta = m; }
		@Override
		public BPool pool() { return pool; }
		@Override
		public void pool(BPool pool) { this.pool = pool; }
		@Override
		public void close() { if (pool != null) BufferPool.this.reserve(this); }
		@Override
		public void release() { set(ArrayCache.BYTES,0,0); }
	}

	private final BPool[] pools;
	private final ReentrantLock lock = new ReentrantLock();

	private final LeakDetector ldt = LeakDetector.create();

	private static final int SHELL_MAX = 8;
	private final List<PooledBuffer> directShell, heapShell;

	private static final AtomicLong UNPOOLED_MAX = new AtomicLong(Integer.MAX_VALUE);
	private static final BPool UNPOOLED = new BPool() {
		public boolean allocate(boolean direct, int minCapacity, PooledBuffer callback) { return false; }
		public boolean reserve(DynByteBuf buf) { return false; }
	};

	public BufferPool(BPool... pools) {
		this.pools = pools;

		directShell = new SimpleList<>(8);
		heapShell = new SimpleList<>(8);
	}

	public final DynByteBuf buffer(boolean direct, int cap) {
		if (cap < 0) throw new IllegalArgumentException("size < 0");

		PooledBuffer buf;
		lock.lock();
		try {
			List<PooledBuffer> list = direct ? directShell : heapShell;
			buf = list.isEmpty() ? direct ? new PooledDirectBuf() : new PooledHeapBuf() : list.remove(list.size()-1);

			for (BPool pool : pools) {
				if (pool.allocate(direct, cap, buf)) {
					if (ldt != null) ldt.track(buf);
					buf.pool(pool);
					return (DynByteBuf) buf;
				}
			}
		} finally {
			lock.unlock();
		}

		if (UNPOOLED_MAX.addAndGet(-cap) < 0) {
			long remain = UNPOOLED_MAX.addAndGet(cap);
			throw new OutOfMemoryError("UNPOOLED_MAX="+remain);
		}

		if (direct) {
			NativeMemory mem = new NativeMemory(cap);
			buf.set(mem, mem.address(), cap);
		} else {
			byte[] b = ArrayCache.getDefaultCache().getByteArray(cap, false);
			buf.set(b, 0, cap);
		}
		buf.pool(UNPOOLED);

		if (ldt != null) ldt.track(buf);
		return (DynByteBuf) buf;
	}

	public final void reserve(DynByteBuf buf) {
		PooledBuffer pb = (PooledBuffer) buf;
		BPool pool;
		synchronized (pb) {
			pool = pb.pool();
			pb.pool(null);
		}
		if (pool == null) throwUnpooled(buf);

		lock.lock();
		try {
			if (ldt != null) ldt.remove(buf);

			if (!pool.reserve(buf)) {
				if (pool == UNPOOLED) UNPOOLED_MAX.addAndGet(buf.capacity());
				manualRelease(buf);
			}

			pb.release();
			List<PooledBuffer> list = buf.isDirect() ? directShell : heapShell;
			if (list.size() < SHELL_MAX) list.add(Helpers.cast(buf));
		} finally {
			lock.unlock();
		}
	}

	private static void manualRelease(DynByteBuf buf) {
		if (buf.isDirect()) ((DirectByteList) buf)._free();
		else ((ByteList) buf)._free();
	}

	public final boolean isPooled(DynByteBuf buf) { return buf instanceof PooledBuffer; }

	public final DynByteBuf expand(DynByteBuf buf, int more) { return expand(buf, more, true, true); }
	public final DynByteBuf expand(DynByteBuf buf, int more, boolean addAtEnd) { return expand(buf, more, addAtEnd, true); }
	public final DynByteBuf expand(DynByteBuf buf, int more, boolean addAtEnd, boolean reserveOld) {
		if (more < 0) throw new IllegalArgumentException("size < 0");

		lock.lock();
		try {
			BPool pool = buf instanceof PooledBuffer ? ((PooledBuffer) buf).pool() : null;
			if (pool == null) {
				if (reserveOld) throwUnpooled(buf);
			} else if (pool.expand(buf, more, addAtEnd)) return buf;

			DynByteBuf newBuf = buffer(buf.isDirect(), buf.capacity()+more);
			if (!addAtEnd) newBuf.wIndex(more);
			newBuf.put(buf);
			if (reserveOld) reserve(buf);
			return newBuf;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public String toString() {
		return "BufferPool{" + Arrays.toString(pools) + '}';
	}

	private static void throwUnpooled(DynByteBuf buf) {
		throw new RuntimeException("已释放的缓冲区: " + buf.info() + "@" + System.identityHashCode(buf));
	}
}