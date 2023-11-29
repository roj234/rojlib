package roj.io.buf;

import roj.collect.IntMap;
import roj.concurrent.SegmentReadWriteLock;
import roj.util.*;
import sun.misc.Unsafe;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static roj.reflect.ReflectionUtils.fieldOffset;
import static roj.reflect.ReflectionUtils.u;
import static roj.util.ArrayCache.DEBUG_DISABLE_CACHE;

/**
 * @author Roj233
 * @since 2022/6/1 7:06
 */
public final class BufferPool {
	private static final int TL_HEAP_INITIAL = 65536, TL_HEAP_MAX = 262144, TL_HEAP_INCR = 65536;
	private static final int TL_DIRECT_INITIAL = 65536, TL_DIRECT_MAX = 262144, TL_DIRECT_INCR = 65536;

	private static final int GLOBAL_HEAP_SIZE = 1048576, GLOBAL_DIRECT_SIZE = 4194304;
	private static final int UNPOOLED_SIZE = 10485760, RESERVED_FOR_EXPAND = 16;

	private static final class PooledDirectBuf extends DirectByteList.Slice implements PooledBuffer {
		private static final long u_pool = fieldOffset(PooledDirectBuf.class, "pool");
		private volatile Object pool;
		@Override
		public Object pool(Object p1) { return u.getAndSetObject(this, u_pool, p1); }

		private Page page;
		@Override
		public Page page() { return page; }
		@Override
		public void page(Page p) { page = p; }

		private int meta;
		@Override
		public int getMetadata() { return meta; }
		@Override
		public void setMetadata(int m) { meta = m; }

		@Override
		public void close() { if (pool != null) BufferPool.reserve(this); }
		@Override
		public void release() { set(null,0L,0); }
	}
	private static final class PooledHeapBuf extends ByteList.Slice implements PooledBuffer {
		private static final long u_pool = fieldOffset(PooledHeapBuf.class, "pool");
		private volatile Object pool;
		@Override
		public Object pool(Object p1) { return u.getAndSetObject(this, u_pool, p1); }

		private Page page;
		@Override
		public Page page() { return page; }
		@Override
		public void page(Page p) { page = p; }

		private int meta;
		@Override
		public int getMetadata() { return meta; }
		@Override
		public void setMetadata(int m) { meta = m; }

		@Override
		public void close() { if (pool != null) BufferPool.reserve(this); }
		@Override
		public void release() { set(ArrayCache.BYTES,0,0); }
	}

	private static final ThreadLocal<BufferPool> DEFAULT = ThreadLocal.withInitial(BufferPool::new);
	public static BufferPool localPool() { return DEFAULT.get(); }

	private static final long
		u_directShellLen = fieldOffset(BufferPool.class, "directShellLen"),
		u_heapShellLen = fieldOffset(BufferPool.class, "heapShellLen");

	private static final AtomicLong UNPOOLED_REMAIN = new AtomicLong(UNPOOLED_SIZE);
	private static final Object _UNPOOLED = IntMap.UNDEFINED;

	private static final ReentrantLock lgDirect = new ReentrantLock(), lgHeap = new ReentrantLock();
	private static final Page pgDirect = Page.create(GLOBAL_DIRECT_SIZE), pgHeap = Page.create(GLOBAL_HEAP_SIZE);
	private static long gDirect;
	private static byte[] gHeap;

	private final LeakDetector ldt = LeakDetector.create();

	private final int heapIncr, heapMax, directIncr, directMax;

	private final SegmentReadWriteLock lock = new SegmentReadWriteLock();
	private Page pDirect, pHeap;
	private long directAddr;
	private volatile NativeMemory directRef;
	private volatile byte[] heap;

	private final PooledBuffer[] directShell, heapShell;
	private int directShellLen, heapShellLen;


	private final boolean useGlobal;

	private BufferPool() { this(TL_DIRECT_INITIAL, TL_DIRECT_INCR, TL_DIRECT_MAX, TL_HEAP_INITIAL, TL_HEAP_INCR, TL_HEAP_MAX, 15, true); }
	public BufferPool(int directInit, int directIncr, int directMax,
					  int heapInit, int heapIncr, int heapMax,
					  int shellSize, boolean useGlobal) {
		this.pHeap = Page.create(heapInit);
		this.heapIncr = heapIncr;
		this.heapMax = heapMax;
		this.pDirect = Page.create(directInit);
		this.directIncr = directIncr;
		this.directMax = directMax;
		this.directShell = directInit <= 0 ? null : new PooledBuffer[shellSize];
		this.heapShell = heapInit <= 0 ? null : new PooledBuffer[shellSize];
		this.useGlobal = useGlobal;
	}

	public static DynByteBuf buffer(boolean direct, int cap) { return localPool().allocate(direct, cap); }
	public DynByteBuf allocate(boolean direct, int cap) {
		if (DEBUG_DISABLE_CACHE) return direct ? DirectByteList.allocateDirect(cap) : ByteList.allocate(cap);
		if (cap < 0) throw new IllegalArgumentException("size < 0");

		PooledBuffer buf;
		boolean large = useGlobal && cap + RESERVED_FOR_EXPAND >= (direct ? directIncr : heapIncr) / 2;
		if (direct) {
			buf = getShell(directShell, u_directShellLen);
			if (buf == null) buf = new PooledDirectBuf();

			if (allocDirect(large, cap, buf)) {
				buf.pool(this);
				if (ldt != null) ldt.track(buf);
				return (DynByteBuf) buf;
			}
		} else {
			buf = getShell(heapShell, u_heapShellLen);
			if (buf == null) buf = new PooledHeapBuf();

			if (allocHeap(large, cap, buf)) {
				buf.pool(this);
				if (ldt != null) ldt.track(buf);
				return (DynByteBuf) buf;
			}
		}

		if (UNPOOLED_REMAIN.addAndGet(-cap) < 0) {
			long remain = UNPOOLED_REMAIN.addAndGet(cap);
			throw new OutOfMemoryError("ThreadLocal, Global, and Unpooled buffer pool are exhausted="+remain);
		}

		if (direct) {
			NativeMemory mem = new NativeMemory(cap);
			buf.set(mem, mem.address(), cap);
		} else {
			byte[] b = ArrayCache.getByteArray(cap, false);
			buf.set(b, 0, b.length);
		}
		buf.pool(_UNPOOLED);

		return (DynByteBuf) buf;
	}
	private PooledBuffer getShell(PooledBuffer[] array, long offset) {
		int len = u.getIntVolatile(this, offset);
		if (len == 0) return null;

		for (int i = array.length-1; i >= 0; i--) {
			long o = Unsafe.ARRAY_OBJECT_BASE_OFFSET + i * Unsafe.ARRAY_OBJECT_INDEX_SCALE;

			Object b = u.getObjectVolatile(array, o);
			if (b == null) continue;

			if (u.compareAndSwapObject(array, o, b, null)) {
				while (true) {
					len = u.getIntVolatile(this, offset);
					if (u.compareAndSwapInt(this, offset, len, len-1))
						return (PooledBuffer) b;
				}
			}
		}
		return null;
	}
	private boolean allocDirect(boolean large, int cap, PooledBuffer sh) {
		block:
		if (!large && directRef == null) {
			lock.lock(0);
			try {
				if (directRef != null) break block;
				directRef = new NativeMemory((int) pDirect.freeSpace());
				directAddr = directRef.address();
			} finally {
				lock.unlock(0);
			}
		}

		sh.setMetadata(RESERVED_FOR_EXPAND);

		int off;
		if (!large) while (true) {
			Page p = pDirect;
			NativeMemory stamp = directRef;

			int slot = System.identityHashCode(p);
			lock.lock(slot);
			try {
				off = (int) p.alloc(cap+RESERVED_FOR_EXPAND);
			} finally {
				lock.unlock(slot);
			}

			if (off >= 0) {
				NativeMemory nm = directRef;
				sh.set(nm, directAddr+off+RESERVED_FOR_EXPAND, cap);

				// ensure we got the address associated with that stamp
				if (nm == stamp) {
					sh.page(p);
					return true;
				}
			}

			if (cap > p.totalSpace()+directIncr || p.totalSpace() == directMax) break;

			lock.lock(0);
			try {
				if (pDirect == p) {
					p = Page.create(Math.min(p.totalSpace() + directIncr, directMax));

					directRef = new NativeMemory((int) p.totalSpace());
					directAddr = directRef.address();

					pDirect = p;
				}
			} finally {
				lock.unlock(0);
			}
		}

		try {
			if (!useGlobal || !lgDirect.tryLock(16, TimeUnit.MILLISECONDS)) return false;
		} catch (InterruptedException ignored) {}

		try {
			if (gDirect == 0) {
				NativeMemory.reserveMemory(GLOBAL_DIRECT_SIZE);
				try {
					gDirect = u.allocateMemory(GLOBAL_DIRECT_SIZE);
				} catch (OutOfMemoryError e) {
					NativeMemory.unreserveMemory(GLOBAL_DIRECT_SIZE);
					throw e;
				}
			}

			off = (int) pgDirect.alloc(cap+RESERVED_FOR_EXPAND);
			if (off >= 0) {
				sh.set(null, gDirect+off+RESERVED_FOR_EXPAND, cap);
				return true;
			}
		} finally {
			lgDirect.unlock();
		}

		return false;
	}
	private boolean allocHeap(boolean large, int cap, PooledBuffer sh) {
		block:
		if (!large && heap == null) {
			lock.lock(1);
			try {
				if (heap != null) break block;
				heap = new byte[(int) pHeap.freeSpace()];
			} finally {
				lock.unlock(1);
			}
		}

		sh.setMetadata(RESERVED_FOR_EXPAND);

		int off;
		if (!large) while (true) {
			Page p = pHeap;
			byte[] stamp = heap;

			int slot = System.identityHashCode(p);
			lock.lock(slot);
			try {
				off = (int) p.alloc(cap+RESERVED_FOR_EXPAND);
			} finally {
				lock.unlock(slot);
			}

			if (off >= 0) {
				byte[] b = heap;
				sh.set(b, off+RESERVED_FOR_EXPAND, cap);

				// ensure we got the address associated with that stamp
				if (b == stamp) {
					sh.page(p);
					return true;
				}
			}

			if (cap > p.totalSpace()+heapIncr || p.totalSpace() == heapMax) break;

			lock.lock(1);
			try {
				if (pHeap == p) {
					p = Page.create(Math.min(p.totalSpace()+heapIncr, heapMax));

					heap = new byte[(int) p.totalSpace()];

					pHeap = p;
				}
			} finally {
				lock.unlock(1);
			}
		}

		try {
			if (!useGlobal || !lgHeap.tryLock(16, TimeUnit.MILLISECONDS)) return false;
		} catch (InterruptedException ignored) {}

		try {
			if (gHeap == null) gHeap = new byte[GLOBAL_HEAP_SIZE];

			off = (int) pgHeap.alloc(cap+RESERVED_FOR_EXPAND);
			if (off >= 0) {
				sh.set(gHeap, off+RESERVED_FOR_EXPAND, cap);
				return true;
			}
		} finally {
			lgHeap.unlock();
		}

		return false;
	}

	public static boolean isPooled(DynByteBuf buf) { return buf instanceof PooledBuffer; }

	public static void reserve(DynByteBuf buf) {
		if (DEBUG_DISABLE_CACHE) return;
		Object pool = ((PooledBuffer) buf).pool(null);

		if (pool == null) throwUnpooled(buf);
		else if (pool != _UNPOOLED) ((BufferPool) pool).reserve0(buf);

		UNPOOLED_REMAIN.addAndGet(buf.capacity());

		if (buf.isDirect()) ((DirectByteList) buf)._free();
		else ((ByteList) buf)._free();
	}
	private void reserve0(DynByteBuf buf) {
		if (ldt != null) ldt.remove(buf);

		PooledBuffer pb = (PooledBuffer) buf;

		int prefix = pb.getMetadata();
		if (buf.isDirect()) {
			long m = buf.address();
			NativeMemory nm = ((DirectByteList) buf).memory();
			block: {
				if (nm == null) {
					lgDirect.lock();
					try {
						pgDirect.free(m-gDirect-prefix, buf.capacity()+prefix);
					} finally {
						lgDirect.unlock();
					}
					break block;
				}

				Page p = pb.page();
				int slot = System.identityHashCode(p);
				lock.lock(slot);
				try {
					p.free(m-directAddr-prefix, buf.capacity()+prefix);
				} finally {
					lock.unlock(slot);
				}
			}
			pb.release();
			addShell(directShell, u_directShellLen, pb);
		} else {
			block: {
				byte[] bb = buf.array();
				if (bb == gHeap) {
					lgHeap.lock();
					try {
						pgHeap.free(buf.arrayOffset()-prefix, buf.capacity()+prefix);
					} finally {
						lgHeap.unlock();
					}
					break block;
				}

				Page p = pb.page();
				int slot = System.identityHashCode(p);
				lock.lock(slot);
				try {
					p.free(buf.arrayOffset()-prefix, buf.capacity()+prefix);
				} finally {
					lock.unlock(slot);
				}
			}

			pb.release();
			addShell(heapShell, u_heapShellLen, pb);
		}
	}
	private void addShell(PooledBuffer[] array, long offset, PooledBuffer b1) {
		int len = u.getIntVolatile(this, offset);
		if (len >= array.length) return;

		for (int i = 0; i < array.length; i++) {
			long o = Unsafe.ARRAY_OBJECT_BASE_OFFSET + i * Unsafe.ARRAY_OBJECT_INDEX_SCALE;

			Object b = u.getObjectVolatile(array, o);
			if (b != null) continue;

			if (u.compareAndSwapObject(array, o, null, b1)) {
				while (true) {
					len = u.getIntVolatile(this, offset);
					if (u.compareAndSwapInt(this, offset, len, len+1)) return;
				}
			}
		}
	}
	private static void throwUnpooled(DynByteBuf buf) { throw new RuntimeException("已释放的缓冲区: "+buf.info()+"@"+System.identityHashCode(buf)); }

	public static DynByteBuf expand(DynByteBuf buf, int more) { return expand(buf, more, true, true); }
	public static DynByteBuf expand(DynByteBuf buf, int more, boolean addAtEnd) { return expand(buf, more, addAtEnd, true); }
	public static DynByteBuf expand(DynByteBuf buf, int more, boolean addAtEnd, boolean reserveOld) {
		if (more < 0) throw new IllegalArgumentException("size < 0");

		Object pool;
		if (!(buf instanceof PooledBuffer)) {
			if (reserveOld) throwUnpooled(buf);
			pool = null;
		} else {
			// 禁止异步reserve
			pool = ((PooledBuffer) buf).pool(null);
			if (pool == null) throwUnpooled(buf);
			else if (pool != _UNPOOLED && !DEBUG_DISABLE_CACHE && tryZeroCopy(buf, more, addAtEnd, (BufferPool) pool, (PooledBuffer) buf)) {
				((PooledBuffer) buf).pool(pool);
				return buf;
			}
		}

		if (pool != null) ((PooledBuffer) buf).pool(pool);

		DynByteBuf newBuf = buffer(buf.isDirect(), buf.capacity()+more);
		if (!addAtEnd) newBuf.wIndex(more);
		newBuf.put(buf);
		if (reserveOld) reserve(buf);
		return newBuf;
	}
	private static boolean tryZeroCopy(DynByteBuf buf, int more, boolean addAtEnd, BufferPool p, PooledBuffer pb) {
		Page page;
		Lock lock;
		int offset;

		if (buf.isDirect()) {
			long m = buf.address();
			NativeMemory nm = ((DirectByteList) buf).memory();
			if (nm == null) {
				page = pgDirect;
				lock = lgDirect;
				offset = (int) (buf.address()-gDirect);
			} else {
				page = pb.page();
				lock = p.lock.asReadLock(System.identityHashCode(page));
				offset = (int) (buf.address()-p.directAddr);
			}
		} else {
			offset = buf.arrayOffset();
			if (buf.array() == gHeap) {
				page = pgHeap;
				lock = lgHeap;
			} else {
				page = pb.page();
				lock = p.lock.asReadLock(System.identityHashCode(page));
			}
		}

		DynByteBuf b = ((DynByteBuf) pb);
		if (addAtEnd) {
			lock.lock();
			try {
				if (!page.allocAfter(offset - pb.getMetadata(), b.capacity() + pb.getMetadata(), more)) return false;
			} finally {
				lock.unlock();
			}
			pb._expand(more, false);
		} else {
			if (pb.getMetadata() >= more) pb.setMetadata(pb.getMetadata() - more);
			else {
				lock.lock();
				try {
					if (!page.allocBefore(offset - pb.getMetadata(), b.capacity() + pb.getMetadata(), more)) return false;
				} finally {
					lock.unlock();
				}
			}

			pb._expand(more, true);
		}
		return true;
	}
}