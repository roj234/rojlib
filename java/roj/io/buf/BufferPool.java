package roj.io.buf;

import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.concurrent.SegmentReadWriteLock;
import roj.concurrent.task.ITask;
import roj.concurrent.timing.Scheduler;
import roj.plugin.Status;
import roj.text.CharList;
import roj.text.logging.Logger;
import roj.util.*;
import sun.misc.Unsafe;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

import static roj.reflect.ReflectionUtils.fieldOffset;
import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj233
 * @since 2022/6/1 7:06
 */
public final class BufferPool {
	// 超过128KB的从ArrayCache拿，本地缓存不大于4MB
	private static final int HEAP_INIT = 32768, HEAP_INCR = 32768, HEAP_FLEX_MAX = 4194304, HEAP_LARGE = 131072;
	// 超过4MB的调用系统分配，本地缓存不大于16MB
	private static final int DIRECT_INIT = 32768, DIRECT_INCR = 32768, DIRECT_FLEX_MAX = 16777216, DIRECT_LARGE = 4194304;
	private static final int DEFAULT_KEEP_BEFORE = 16;

	private static final class PooledDirectBuf extends DirectByteList.Slice implements PooledBuffer {
		private static final long u_pool = fieldOffset(PooledDirectBuf.class, "pool");
		private volatile Object pool;
		@Override public Object pool(Object p1) { return u.getAndSetObject(this, u_pool, p1); }

		private Page page;
		@Override public Page page() { return page; }
		@Override public void page(Page p) { page = p; }

		private int meta;
		@Override public int getKeepBefore() { return meta; }
		@Override public void setKeepBefore(int keepBefore) { meta = keepBefore; }

		@Override public void close() { if (pool != null) BufferPool.reserve(this); }
		@Override public void release() { set(null,0L,0); }
	}
	private static final class PooledHeapBuf extends ByteList.Slice implements PooledBuffer {
		private static final long u_pool = fieldOffset(PooledHeapBuf.class, "pool");
		private volatile Object pool;
		@Override
		public Object pool(Object p1) { return u.getAndSetObject(this, u_pool, p1); }

		private Page page;
		@Override public Page page() { return page; }
		@Override public void page(Page p) { page = p; }

		private int meta;
		@Override public int getKeepBefore() { return meta; }
		@Override public void setKeepBefore(int keepBefore) { meta = keepBefore; }

		@Override public void close() { if (pool != null) BufferPool.reserve(this); }
		@Override public void release() { set(ArrayCache.BYTES,0,0); }
	}

	private static final ThreadLocal<BufferPool> DEFAULT = ThreadLocal.withInitial(BufferPool::new);
	public static BufferPool localPool() { return DEFAULT.get(); }

	private static final long
		u_directShellLen = fieldOffset(BufferPool.class, "directShellLen"),
		u_heapShellLen = fieldOffset(BufferPool.class, "heapShellLen"),
		u_heap = fieldOffset(BufferPool.class, "heap"),
		u_directRef = fieldOffset(BufferPool.class, "directRef");

	private static final Logger LOGGER = Logger.getLogger();
	private static final Object _UNPOOLED = IntMap.UNDEFINED;

	private static final PooledHeapBuf EMPTY_HEAP_SENTIAL = new PooledHeapBuf();
	private static final PooledDirectBuf EMPTY_DIRECT_SENTIAL = new PooledDirectBuf();

	private final LeakDetector ldt = LeakDetector.create();

	private final int heapInit, heapIncr, heapMax;
	private final long directInit, directIncr, directMax;

	public long getDirectMax() { return directMax; }
	public int getHeapMax() { return heapMax; }

	private final SegmentReadWriteLock lock = new SegmentReadWriteLock();
	private Page pDirect, pHeap;
	private volatile NativeMemory directRef;
	private volatile byte[] heap;

	private final PooledBuffer[] directShell, heapShell;
	private int directShellLen, heapShellLen;

	private final SimpleList<ByteBuffer> directBufferShell = new SimpleList<>();

	private boolean hasDelay;
	private final int maxStall;
	private final ITask stallReleaseTask;
	private long accessTimestamp;

	private BufferPool() {this(DIRECT_INIT, DIRECT_INCR, DIRECT_FLEX_MAX, HEAP_INIT, HEAP_INCR, HEAP_FLEX_MAX, 15, 60000);}
	public BufferPool(long directInit, long directIncr, long directMax,
					  int heapInit, int heapIncr, int heapMax,
					  int shellSize, int maxStall) {
		this.directInit = directInit;
		this.heapInit = heapInit;

		this.pHeap = heapInit <= 0 ? null : Page.create(heapInit);
		this.heapIncr = heapIncr;
		this.heapMax = heapMax;
		this.pDirect = directInit <= 0 ? null : Page.create(directInit);
		this.directIncr = directIncr;
		this.directMax = directMax;
		this.directShell = directInit <= 0 ? null : new PooledBuffer[shellSize];
		this.heapShell = heapInit <= 0 ? null : new PooledBuffer[shellSize];

		this.maxStall = maxStall;
		this.stallReleaseTask = getTask(new WeakReference<>(this));
	}

	private void tryDelayedFree() {
		if (!hasDelay) {
			Scheduler.getDefaultScheduler().delay(stallReleaseTask, maxStall);
			hasDelay = true;
		} else {
			//reset delay
			accessTimestamp = System.currentTimeMillis();
		}
	}
	private static ITask getTask(WeakReference<BufferPool> pool) {
		return () -> {
			BufferPool p = pool.get();
			if (p == null || System.currentTimeMillis() - p.accessTimestamp < p.maxStall) return;

			if (p.directRef != null && p.pDirect.usedSpace() == 0) freeDirect(p);
			if (p.heap != null && p.pHeap.usedSpace() == 0) freeHeap(p);

			p.hasDelay = false;
		};
	}
	public void release() {
		if (pHeap != null) {
			if (pHeap.usedSpace() != 0) throw new IllegalStateException(pHeap.toString());
			else freeHeap(this);
		}

		if (pDirect != null) {
			if (pDirect.usedSpace() != 0) throw new IllegalStateException(pDirect.toString());
			else freeDirect(this);
		}
	}
	private static void freeDirect(BufferPool p) {
		p.lock.lock(0);
		try {
			if (p.pDirect.usedSpace() == 0) {
				if (p.directRef != null) {
					p.directRef.release();
					p.directRef = null;
				}
				p.directBufferShell.clear();
				p.pDirect = Page.create(Math.max(p.directInit, (int) p.pDirect.totalSpace() - p.directIncr));
			}
		} finally {
			p.lock.unlock(0);
		}
	}
	private static void freeHeap(BufferPool p) {
		p.lock.lock(1);
		try {
			if (p.pHeap.usedSpace() == 0) {
				p.heap = null;
				p.pHeap = Page.create(Math.max(p.heapInit, (int) p.pHeap.totalSpace() - p.heapIncr));
			}
		} finally {
			p.lock.unlock(1);
		}
	}

	public static DynByteBuf buffer(boolean direct, int cap) { return localPool().allocate(direct, cap); }
	public DynByteBuf allocate(boolean direct, int cap) { return allocate(direct, cap, DEFAULT_KEEP_BEFORE); }
	public DynByteBuf allocate(boolean direct, int cap, int keepBefore) {
		if ((cap|keepBefore) < 0) throw new IllegalArgumentException("size < 0");

		cap += keepBefore;
		if (cap == 0) return direct ? EMPTY_DIRECT_SENTIAL : EMPTY_HEAP_SENTIAL;

		PooledBuffer buf;
		if (direct) {
			buf = getShell(directShell, u_directShellLen);
			if (buf == null) buf = new PooledDirectBuf();
			buf.setKeepBefore(keepBefore);

			if (allocDirect(cap, buf) != 0) {
				buf.pool(this);
				if (ldt != null) ldt.track(buf);
				return (DynByteBuf) buf;
			}
		} else {
			buf = getShell(heapShell, u_heapShellLen);
			if (buf == null) buf = new PooledHeapBuf();
			buf.setKeepBefore(keepBefore);

			if (allocHeap(cap, buf)) {
				buf.pool(this);
				if (ldt != null) ldt.track(buf);
				return (DynByteBuf) buf;
			}
		}

		if (direct) {
			var mem = new NativeMemory(cap);
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
	private long allocDirect(long cap, PooledBuffer sh) {
		long off;
		if (cap < DIRECT_LARGE) while (true) {
			Page p = pDirect;
			NativeMemory stamp = directRef;
			if (stamp == null && !u.compareAndSwapObject(this, u_directRef, null, stamp = new NativeMemory(p.totalSpace()))) {
				stamp.release();
				continue;
			}

			int slot = System.identityHashCode(p);
			lock.lock(slot);
			try {
				off = p.alloc(cap);
			} finally {
				lock.unlock(slot);
			}

			// ensure we got the address associated with that stamp
			if (off >= 0 && directRef == stamp) {
				long base = stamp.address()+off;
				if (sh != null) {
					sh.set(stamp, base+sh.getKeepBefore(), (int) (cap-sh.getKeepBefore()));
					sh.page(p);
				}
				return base;
			}

			if (p.totalSpace()+cap > directMax) break;

			lock.lock(0);
			try {
				if (pDirect == p) {
					// otherwise give it to GC
					if (p.usedSpace() == 0) directRef.release();

					long space = Math.min(p.totalSpace() + ((cap+directIncr-1)/directIncr)*directIncr, directMax);
					p = Page.create(space);
					directRef = new NativeMemory(p.totalSpace());
					directBufferShell.clear();
					pDirect = p;
				}
			} finally {
				lock.unlock(0);
			}
		}

		return 0;
	}
	private boolean allocHeap(int cap, PooledBuffer sh) {
		int off;
		if(cap < HEAP_LARGE) while (true) {
			Page p = pHeap;
			byte[] stamp = heap;
			if (stamp == null && !u.compareAndSwapObject(this, u_heap, null, stamp = new byte[(int) p.totalSpace()]))
				continue;

			int slot = System.identityHashCode(p);
			lock.lock(slot);
			try {
				off = (int) p.alloc(cap);
			} finally {
				lock.unlock(slot);
			}

			// ensure we got the address associated with that stamp
			if (off >= 0 && heap == stamp) {
				sh.set(stamp, off+sh.getKeepBefore(), cap-sh.getKeepBefore());
				sh.page(p);
				return true;
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

		return false;
	}

	public long malloc(long size) {
		if (pDirect.totalSpace() < directMax) throw new UnsupportedOperationException("only non-extensible pool can direct allocate");
		if (size < 0) throw new IllegalArgumentException("size < 0");
		if (size == 0) return 0;

		size += 16;
		long addr = allocDirect(size, null);
		if (addr == 0) return 0;

		u.putLong(addr, size);
		u.putLong(addr+8, ~size);

		return addr+16;
	}
	public void free(long address) {
		if (address == 0) return;
		long cap1 = ~u.getLong(address -= 8);
		long cap2 = u.getLong(address -= 8);
		if (cap1 != cap2 || cap1 == 0) throw new UnsupportedOperationException("memory segment mangled");

		int slot = System.identityHashCode(pDirect);
		lock.lock(slot);
		try {
			pDirect.free(address-directRef.address(), cap1);
		} finally {
			lock.unlock(slot);
		}
	}

	public static ByteBuffer mallocShell(DynByteBuf buf) {
		if (buf instanceof PooledDirectBuf pb) {
			var nm = pb.memory();
			var pool = (BufferPool) pb.pool;
			pool.lock.lock(0);
			try {
				if (pool.directRef == nm) {
					var shell = pool.directBufferShell.pop();
					if (shell != null) {
						NativeMemory.setBufferCapacityAndAddress(shell, pb.address(), pb.capacity());
						return shell.limit(pb.wIndex()).position(pb.rIndex);
					}
				}
			} finally {
				pool.lock.unlock(0);
			}
		}

		return buf.nioBuffer();
	}
	public static void mfreeShell(DynByteBuf buf, ByteBuffer shell) {
		if (!(buf instanceof PooledDirectBuf pb)) return;
		var nm = pb.memory();
		var pool = (BufferPool) pb.pool;
		if (pool.directRef != nm) return;

		pool.lock.lock(0);
		try {
			if (pool.directRef == nm) {
				NativeMemory.setBufferCapacityAndAddress(shell, 0, 0);
				pool.directBufferShell.add(shell);
			}
		} finally {
			pool.lock.unlock(0);
		}
	}

	public static boolean isPooled(DynByteBuf buf) { return buf instanceof PooledBuffer; }

	public static void reserve(DynByteBuf buf) {
		Object pool = buf instanceof PooledBuffer pb ? pb.pool(null) : _UNPOOLED;
		if (pool == null) {
			if (buf != EMPTY_DIRECT_SENTIAL && buf != EMPTY_HEAP_SENTIAL)
				LOGGER.warn("已释放的缓冲区: "+buf.info()+"@"+System.identityHashCode(buf));
		} else if (pool != _UNPOOLED) ((BufferPool) pool).reserve0(buf);
		else {
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
			Page p = pb.page();
			int slot = System.identityHashCode(p);
			lock.lock(slot);
			try {
				p.free(m-nm.address()-prefix, buf.capacity()+prefix);
				if (p.usedSpace() == 0) {
					if (directRef != nm) nm.release();
					else tryDelayedFree();
				}
			} finally {
				lock.unlock(slot);
			}

			pb.release();
			addShell(directShell, u_directShellLen, pb);
		} else {
			byte[] bb = buf.array();
			Page p = pb.page();
			int slot = System.identityHashCode(p);
			lock.lock(slot);
			try {
				p.free(buf.arrayOffset()-prefix, buf.capacity()+prefix);
				if (p.usedSpace() == 0) {
					if (heap != bb) ArrayCache.putArray(bb);
					else tryDelayedFree();
				}
			} finally {
				lock.unlock(slot);
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

	public DynByteBuf expand(DynByteBuf buf, int more) { return expand(buf, more, true, true); }
	public DynByteBuf expandBefore(DynByteBuf buf, int more) { return expand(buf, more, false, false); }
	public DynByteBuf expand(DynByteBuf buf, int more, boolean addAtEnd, boolean reserveOld) {
		Object pool;
		if (!(buf instanceof PooledBuffer)) {
			if (reserveOld) throwUnpooled(buf);
			pool = null;
		} else {
			// 禁止异步reserve
			pool = ((PooledBuffer) buf).pool(null);
			if (pool == null) {
				if (buf != EMPTY_DIRECT_SENTIAL && buf != EMPTY_HEAP_SENTIAL)
					throwUnpooled(buf);
			} else if(pool == _UNPOOLED
					? tryZeroCopyExt(more, addAtEnd, (PooledBuffer) buf)
					: tryZeroCopy(more, addAtEnd, (BufferPool) pool, (PooledBuffer) buf)) {
				((PooledBuffer) buf).pool(pool);
				__expand_1++;
				return buf;
			}
		}

		if (pool != null) ((PooledBuffer) buf).pool(pool);
		if (more < 0) return buf;

		__expand_2++;
		DynByteBuf newBuf = allocate(buf.isDirect(), buf.capacity()+more);
		if (!addAtEnd) newBuf.wIndex(more);
		newBuf.put(buf);
		if (reserveOld) reserve(buf);
		return newBuf;
	}
	private static boolean tryZeroCopy(int more, boolean addAtEnd, BufferPool p, PooledBuffer pb) {
		var b = (DynByteBuf) pb;
		var offset = b.isDirect() ? (int) (b.address() - ((DirectByteList) b).memory().address()) : b.arrayOffset();
		var page = pb.page();
		var lock = p.lock.asReadLock(System.identityHashCode(page));

		int prefix = pb.getKeepBefore();
		if (addAtEnd) {
			lock.lock();
			try {
				if (more < 0) {
					int x = -more & ~7;
					if (x > 0) {
						int off = (int) Page.align(offset + b.capacity()) - x;
						page.free(off, x);
					} else {
						LOGGER.debug("FreeTooSmall: "+b.info()+", size="+more+" => 0");
					}
				} else if (!page.allocAfter(offset - prefix, b.capacity() + prefix, more)) return false;
			} finally {
				lock.unlock();
			}
			pb._expand(more, false);
		} else {
			if (prefix >= more) {
				pb.setKeepBefore(prefix - more);
			} else {
				lock.lock();
				try {
					if (!page.allocBefore(offset - prefix, b.capacity() + prefix, more)) return false;
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

	private int __expand_1, __expand_2;
	@Status
	public CharList status(CharList sb) {
		sb.append("本机缓冲池:");
		(directRef == null ? sb.append("未初始化") : pDirect.toString(sb, 0)).append("\n堆缓冲池:");
		(heap==null?sb.append("未初始化"):pHeap.toString(sb, 0)).append("\n缓冲区扩展:");
		return sb.append("预留空间:").append(DEFAULT_KEEP_BEFORE).append(", 零拷贝成功:").append(__expand_1).append(", 调用:").append(__expand_2);
	}
}