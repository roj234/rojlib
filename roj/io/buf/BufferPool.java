package roj.io.buf;

import roj.collect.IntMap;
import roj.concurrent.SegmentReadWriteLock;
import roj.concurrent.task.ITask;
import roj.concurrent.timing.Scheduler;
import roj.util.*;
import sun.misc.Unsafe;

import java.lang.ref.WeakReference;
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
	// 数据瞎填的
	// 另外req >= CAP+INCR也不会用
	private static final int TL_HEAP_INITIAL = 65536, TL_HEAP_INCR = 65536, TL_HEAP_MAX = 16777216, TL_HEAP_THRES = 131072;
	private static final int TL_DIRECT_INITIAL = 65536, TL_DIRECT_INCR = 65536, TL_DIRECT_MAX = 16777216, TL_DIRECT_THRES = 131072;

	private static final int GLOBAL_HEAP_SIZE = 4194304, GLOBAL_DIRECT_SIZE = 4194304;
	private static final int UNPOOLED_SIZE = 10485760, DEFAULT_KEEP_BEFORE = 16;

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
		public int getKeepBefore() { return meta; }
		@Override
		public void setKeepBefore(int keepBefore) { meta = keepBefore; }

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
		public int getKeepBefore() { return meta; }
		@Override
		public void setKeepBefore(int keepBefore) { meta = keepBefore; }

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

	private final int
		heapInit, heapIncr, heapThreshold,
		directInit, directIncr, directThreshold;
	private final long heapMax, directMax;

	private final SegmentReadWriteLock lock = new SegmentReadWriteLock();
	private Page pDirect, pHeap;
	private long directAddr;
	private volatile NativeMemory directRef;
	private volatile byte[] heap;

	private final PooledBuffer[] directShell, heapShell;
	private int directShellLen, heapShellLen;

	private long directTimeStamp, heapTimestamp;
	private boolean hasTask;
	private final int maxStall;
	private final ITask stallReleaseTask;

	private BufferPool() {
		this(TL_DIRECT_INITIAL, TL_DIRECT_INCR, TL_DIRECT_MAX, TL_DIRECT_THRES,
		TL_HEAP_INITIAL, TL_HEAP_INCR, TL_HEAP_MAX, TL_HEAP_THRES, 15, 60000); }

	public BufferPool(int directInit, int directIncr, int directMax, int directGlobalThreshold,
					  int heapInit, int heapIncr, int heapMax, int heapGlobalThreshold,
					  int shellSize, int maxStall) {
		this.directInit = directInit;
		this.heapInit = heapInit;

		this.pHeap = heapInit <= 0 ? null : Page.create(heapInit);
		this.heapIncr = heapIncr;
		this.heapMax = heapMax;
		this.heapThreshold = heapGlobalThreshold;
		this.pDirect = directInit <= 0 ? null : Page.create(directInit);
		this.directIncr = directIncr;
		this.directMax = directMax;
		this.directThreshold = directGlobalThreshold;
		this.directShell = directInit <= 0 ? null : new PooledBuffer[shellSize];
		this.heapShell = heapInit <= 0 ? null : new PooledBuffer[shellSize];

		this.maxStall = maxStall;
		if (maxStall > 0) {
			stallReleaseTask = getTask(new WeakReference<>(this));
		} else {
			stallReleaseTask = null;
			hasTask = true;
		}
	}

	private void scheduleClean() { Scheduler.getDefaultScheduler().delay(stallReleaseTask, maxStall); hasTask = true; }
	private static ITask getTask(WeakReference<BufferPool> pool) {
		return () -> {
			BufferPool p = pool.get();
			if (p == null) return;

			long yy = System.currentTimeMillis();
			if (p.directRef != null && p.pDirect.usedSpace() == 0 && yy - p.directTimeStamp > p.maxStall) {
				p.lock.lock(0);
				try {
					if (p.pDirect.usedSpace() == 0) {
						p.directRef.release();
						p.directRef = null;
						p.pDirect = new Page(Math.min(p.directInit, (int) p.pDirect.totalSpace() - p.directIncr));
					}
				} finally {
					p.lock.unlock(0);
				}
			}
			if (p.heap != null && p.pHeap.usedSpace() == 0 && yy - p.heapTimestamp > p.maxStall) {
				p.lock.lock(1);
				try {
					if (p.pHeap.usedSpace() == 0) {
						p.heap = null;
						p.pHeap = new Page(Math.min(p.heapInit, (int) p.pHeap.totalSpace() - p.heapIncr));
					}
				} finally {
					p.lock.unlock(1);
				}
			}

			if (p.directRef == null && p.heap == null) p.hasTask = false;
			else p.scheduleClean();
		};
	}

	public static DynByteBuf buffer(boolean direct, int cap) { return localPool().allocate(direct, cap); }
	public DynByteBuf allocate(boolean direct, int cap) { return allocate(direct, cap, DEFAULT_KEEP_BEFORE); }
	public DynByteBuf allocate(boolean direct, int cap, int keepBefore) {
		if (DEBUG_DISABLE_CACHE) return direct ? DirectByteList.allocateDirect(cap) : ByteList.allocate(cap);
		if (cap < 0) throw new IllegalArgumentException("size < 0");

		cap += keepBefore;

		PooledBuffer buf;
		int threshold = direct ? directThreshold : heapThreshold;
		boolean large = threshold > 0 && cap >= threshold;
		if (direct) {
			buf = getShell(directShell, u_directShellLen);
			if (buf == null) buf = new PooledDirectBuf();
			buf.setKeepBefore(keepBefore);

			if (allocDirect(large, cap, buf)) {
				if (!hasTask) scheduleClean();
				directTimeStamp = System.currentTimeMillis();
				buf.pool(this);
				if (ldt != null) ldt.track(buf);
				return (DynByteBuf) buf;
			}
		} else {
			buf = getShell(heapShell, u_heapShellLen);
			if (buf == null) buf = new PooledHeapBuf();
			buf.setKeepBefore(keepBefore);

			if (allocHeap(large, cap, buf)) {
				if (!hasTask) scheduleClean();
				heapTimestamp = System.currentTimeMillis();
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
			buf.set(mem, mem.address()+keepBefore, cap-keepBefore);
		} else {
			byte[] b = ArrayCache.getByteArray(cap, false);
			buf.set(b, keepBefore, b.length-keepBefore);
		}
		buf.pool(_UNPOOLED);

		return (DynByteBuf) buf;
	}
	private PooledBuffer getShell(PooledBuffer[] array, long offset) {
		int len = u.getIntVolatile(this, offset);
		if (len == 0) return null;

		for (int i = array.length-1; i >= 0; i--) {
			long o = Unsafe.ARRAY_OBJECT_BASE_OFFSET + (long) i * Unsafe.ARRAY_OBJECT_INDEX_SCALE;

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

		int off;
		if (!large) while (true) {
			Page p = pDirect;
			NativeMemory stamp = directRef;

			int slot = System.identityHashCode(p);
			lock.lock(slot);
			try {
				off = (int) p.alloc(cap);
			} finally {
				lock.unlock(slot);
			}

			if (off >= 0) {
				NativeMemory nm = directRef;

				// ensure we got the address associated with that stamp
				if (nm == stamp) {
					if (sh != null) {
						sh.set(nm, directAddr+off+sh.getKeepBefore(), cap-sh.getKeepBefore());
						sh.page(p);
					}
					return true;
				}
			}

			if (p.totalSpace()+cap > directMax) break;

			lock.lock(0);
			try {
				if (pDirect == p) {
					long space = p.totalSpace() + (long) ((cap+directIncr-1)/directIncr)*directIncr;
					p = Page.create(Math.min(space, directMax));

					directRef = new NativeMemory((int) p.totalSpace());
					directAddr = directRef.address();

					pDirect = p;
				}
			} finally {
				lock.unlock(0);
			}
		}

		try {
			if (directThreshold < 0 || !lgDirect.tryLock(16, TimeUnit.MILLISECONDS)) return false;
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

			off = (int) pgDirect.alloc(cap);
			if (off >= 0) {
				sh.set(null, gDirect+off+sh.getKeepBefore(), cap-sh.getKeepBefore());
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

		int off;
		if (!large) while (true) {
			Page p = pHeap;
			byte[] stamp = heap;

			int slot = System.identityHashCode(p);
			lock.lock(slot);
			try {
				off = (int) p.alloc(cap);
			} finally {
				lock.unlock(slot);
			}

			if (off >= 0) {
				byte[] b = heap;

				// ensure we got the address associated with that stamp
				if (b == stamp) {
					sh.set(b, off+sh.getKeepBefore(), cap-sh.getKeepBefore());
					sh.page(p);
					return true;
				}
			}


			if (p.totalSpace()+cap > heapMax) break;

			lock.lock(1);
			try {
				if (pHeap == p) {
					long space = p.totalSpace() + (long) ((cap+heapIncr-1)/heapIncr)*heapIncr;
					p = Page.create(Math.min(space, heapMax));

					heap = new byte[(int) p.totalSpace()];

					pHeap = p;
				}
			} finally {
				lock.unlock(1);
			}
		}

		try {
			if (heapThreshold < 0 || !lgHeap.tryLock(16, TimeUnit.MILLISECONDS)) return false;
		} catch (InterruptedException ignored) {}

		try {
			if (gHeap == null) gHeap = new byte[GLOBAL_HEAP_SIZE];

			off = (int) pgHeap.alloc(cap);
			if (off >= 0) {
				sh.set(gHeap, off+sh.getKeepBefore(), cap-sh.getKeepBefore());
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
		else {
			UNPOOLED_REMAIN.addAndGet(buf.capacity() + ((PooledBuffer) buf).getKeepBefore());

			if (buf.isDirect()) ((DirectByteList) buf)._free();
			else ((ByteList) buf)._free();
		}
	}
	private void reserve0(DynByteBuf buf) {
		if (ldt != null) ldt.remove(buf);

		PooledBuffer pb = (PooledBuffer) buf;

		int prefix = pb.getKeepBefore();
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
			long o = Unsafe.ARRAY_OBJECT_BASE_OFFSET + (long)i * Unsafe.ARRAY_OBJECT_INDEX_SCALE;

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
		if (more < 0 && (!addAtEnd || !reserveOld)) throw new IllegalArgumentException("size < 0");

		Object pool;
		if (!(buf instanceof PooledBuffer)) {
			if (reserveOld) throwUnpooled(buf);
			pool = null;
		} else {
			// 禁止异步reserve
			pool = ((PooledBuffer) buf).pool(null);
			if (pool == null) throwUnpooled(buf);
			else if(DEBUG_DISABLE_CACHE || pool == _UNPOOLED
					? tryZeroCopyExt(more, addAtEnd, (PooledBuffer) buf)
					: tryZeroCopy(buf, more, addAtEnd, (BufferPool) pool, (PooledBuffer) buf)) {
				((PooledBuffer) buf).pool(pool);
				return buf;
			}
		}

		if (pool != null) ((PooledBuffer) buf).pool(pool);
		if (more < 0) return buf;

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
				if (more < 0) {
					more = (int) Page.align(more+Page.MINIMUM_MASK);
					int off = (int) Page.align(offset + b.capacity()) + more;
					page.free(off, -more);
				} else if (!page.allocAfter(offset - pb.getKeepBefore(), b.capacity() + pb.getKeepBefore(), more)) return false;
			} finally {
				lock.unlock();
			}
			pb._expand(more, false);
		} else {
			if (pb.getKeepBefore() >= more) pb.setKeepBefore(pb.getKeepBefore() - more);
			else {
				lock.lock();
				try {
					if (!page.allocBefore(offset - pb.getKeepBefore(), b.capacity() + pb.getKeepBefore(), more)) return false;
				} finally {
					lock.unlock();
				}
			}

			pb._expand(more, true);
		}
		return true;
	}
	private static boolean tryZeroCopyExt(int more, boolean addAtEnd, PooledBuffer pb) {
		if (addAtEnd || pb.getKeepBefore() < more) return false;
		pb.setKeepBefore(pb.getKeepBefore() - more);
		pb._expand(more, true);
		return true;
	}
}