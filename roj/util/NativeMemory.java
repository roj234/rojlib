package roj.util;

import roj.reflect.DirectAccessor;
import roj.reflect.ReflectionUtils;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.concurrent.locks.ReentrantLock;

import static roj.reflect.FieldAccessor.u;

/**
 * @author Roj233
 * @since 2022/5/31 20:04
 */
public class NativeMemory {
	private interface H {
		int pageSize();

		default void reserveMemory(long size, int cap) {
			reserveMemory2(size, cap);
		}
		void reserveMemory2(long size, long cap);
		default void unreserveMemory(long size, int cap) {
			unreserveMemory2(size, cap);
		}
		void unreserveMemory2(long size, long cap);

		default ByteBuffer newDirectBuffer(long addr, int cap, Object att) {
			return (ByteBuffer) newDirectBuffer2(addr, cap, att, null);
		}
		Object newDirectBuffer2(long addr, int cap, Object att, Object memorySegmentProxy);
		default ByteBuffer newHeapBuffer(byte[] buf, int mark, int pos, int lim, int cap, int off) {
			return (ByteBuffer) newHeapBuffer2(buf, mark, pos, lim, cap, off, null);
		}
		Object newHeapBuffer2(Object buf, int mark, int pos, int lim, int cap, int off, Object memorySegmentProxy);

		default byte[] getHb(ByteBuffer buf) { return null; }
		default int getOffset(ByteBuffer buf) { return 0; }
		void setHb(ByteBuffer buf, byte[] hb);
		void setOffset(ByteBuffer buf, int offset);

		void setCapacity(Buffer b, int newCapacity);
		long getAddress(Buffer b);
		void setAddress(Buffer b, long newAddress);

		Object newCleaner(Object ref, Runnable callback);
		void invokeClean(Object cleaner);

		boolean isDirectMemoryPageAligned();
	}

	static final H hlp;
	static {
		try {
			DirectAccessor<H> da = DirectAccessor.builder(H.class);
			try {
				da.access(ByteBuffer.class, new String[]{"hb","offset"});
			} catch (Exception ignored) {}

			boolean j17 = ReflectionUtils.JAVA_VERSION >= 17;
			boolean j9 = ReflectionUtils.JAVA_VERSION >= 9;
			da.construct(Class.forName("java.nio.DirectByteBuffer"), new String[]{j17?"newDirectBuffer2":"newDirectBuffer"}, j17?Collections.emptyList():null)
			  .construct(Class.forName("java.nio.HeapByteBuffer"), new String[]{j17?"newHeapBuffer2":"newHeapBuffer"}, j17?Collections.emptyList():null)
			  .delegate(Class.forName(j9?"jdk.internal.misc.VM":"sun.misc.VM"), "isDirectMemoryPageAligned")
			  .access(Buffer.class, new String[]{"capacity", "address"}, new String[]{null,"getAddress"}, new String[]{"setCapacity","setAddress"});
			Class<?> cleaner = Class.forName(j9?"jdk.internal.ref.Cleaner" : "sun.misc.Cleaner");
			da.delegate(cleaner, new String[] {"create", "clean"}, new String[] {"newCleaner", "invokeClean"});

			String[] names = {"pageSize", "reserveMemory", "unreserveMemory"};
			hlp = da.delegate(Class.forName("java.nio.Bits"), names, j17 ? new String[] {"pageSize", "reserveMemory2", "unreserveMemory2"} : names).build();
		} catch (ClassNotFoundException e) {
			Helpers.athrow(e);
			throw null;
		}
	}

	public static ByteBuffer newDirectBuffer(long addr, int cap, Object att) {
		return hlp.newDirectBuffer(addr, cap, att);
	}
	public static void setBufferCapacityAndAddress(ByteBuffer buf, long addr, int cap) {
		hlp.setCapacity(buf, cap);
		hlp.setAddress(buf, addr);
	}
	public static ByteBuffer newHeapBuffer(byte[] buf, int mark, int pos, int lim, int cap, int off) {
		return hlp.newHeapBuffer(buf, mark, pos, lim, cap, off);
	}
	public static void cleanNativeMemory(Object cleaner) {
		hlp.invokeClean(cleaner);
	}

	public static byte[] getArray(ByteBuffer buf) { return hlp.getHb(buf); }
	public static int getOffset(ByteBuffer buf) { return hlp.getOffset(buf); }
	public static long getAddress(ByteBuffer buf) { return hlp.getAddress(buf); }

	public NativeMemory() { this(false); }
	public NativeMemory(int size) { this(false); allocate(size); }
	public NativeMemory(boolean zeroFilled) {
		unmanaged = new Unmanaged(zeroFilled);
		hlp.newCleaner(this, unmanaged);
	}

	private final Unmanaged unmanaged;
	private final ReentrantLock lock = new ReentrantLock();

	public long address() {
		return unmanaged.address();
	}
	public long length() {
		return unmanaged.length;
	}

	public synchronized long allocate(int cap) {
		lock.lock();
		try {
			return unmanaged.malloc(cap, false);
		} finally {
			lock.unlock();
		}
	}
	public void release() {
		lock.lock();
		try {
			unmanaged.release();
		} finally {
			lock.unlock();
		}
	}
	@Deprecated
	public long resize(int cap) {
		lock.lock();
		try {
			return unmanaged.resize(cap);
		} finally {
			lock.unlock();
		}
	}

	static final class Unmanaged implements Runnable {
		private static final boolean doAlign = "true".equals(System.getProperty("sun.nio.PageAlignDirectMemory"));

		private final boolean clear;
		private long base, length;
		private int except;

		Unmanaged(boolean b) {clear = b;}

		long address() {
			long base = this.base;
			if (hlp.isDirectMemoryPageAligned()) {
				int ps = hlp.pageSize();
				if (base % ps != 0) return base + ps - (base & (ps - 1));
			}
			return base;
		}

		void release() {
			if (base != 0) {
				u.freeMemory(base);
				hlp.unreserveMemory(length, except);
				base = 0;
			}
		}

		long resize(int cap) {
			long s0 = length;
			int s1 = except;
			long addr = malloc(cap, true);
			hlp.unreserveMemory(s0, s1);
			return addr;
		}

		long malloc(int cap, boolean resize) {
			if (base != 0 && !resize) release();

			boolean pa = hlp.isDirectMemoryPageAligned();
			int ps = hlp.pageSize();
			long size = Math.max(1L, (long) cap + (pa ? ps : 0));
			hlp.reserveMemory(size, cap);

			long base;
			try {
				base = this.base == 0 ? u.allocateMemory(size) : u.reallocateMemory(this.base, size);
			} catch (OutOfMemoryError x) {
				hlp.unreserveMemory(size, cap);
				throw x;
			}

			if (size > length && clear) {
				u.setMemory(base + length, cap - length, (byte) 0);
			}

			long addr;
			if (pa && (base % ps != 0)) {
				// Round up to page boundary
				addr = base + ps - (base & (ps - 1));
			} else {
				addr = base;
			}

			this.base = base;
			this.length = size;
			this.except = cap;
			return addr;
		}

		@Override
		public void run() { release(); }
	}
}
