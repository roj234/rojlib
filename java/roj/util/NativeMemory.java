package roj.util;

import roj.reflect.Bypass;
import roj.reflect.Java22Workaround;
import roj.reflect.ReflectionUtils;
import roj.text.logging.Logger;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj233
 * @since 2022/5/31 20:04
 */
public class NativeMemory {
	@Java22Workaround
	private interface H {
		int pageSize();

		default void reserveMemory(long size, int cap) { reserveMemory2(size, cap); }
		void reserveMemory2(long size, long cap);
		default void unreserveMemory(long size, int cap) { unreserveMemory2(size, cap); }
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

		long address(Object buf);
		Object attachment(Object buf);
		Object cleaner(Object buf);
	}

	static final H hlp;
	static {
		try {
			Bypass<H> da = Bypass.builder(H.class).inline();
			da.access(ByteBuffer.class, new String[]{"hb","offset"});

			var dbb = Class.forName("java.nio.DirectByteBuffer");
			try {
				Class<?> itf = dbb.getInterfaces()[0];
				da.delegate_o(itf, "attachment", "cleaner", "address");
			} catch (Throwable e) {
				Logger.getLogger().warn("无法加载模块 {}", e, "BufferCleaner");
			}

			var j17 = ReflectionUtils.JAVA_VERSION >= 17;
			var j9 = ReflectionUtils.JAVA_VERSION >= 9;
			da.construct(dbb, new String[]{j17?"newDirectBuffer2":"newDirectBuffer"}, j17?Collections.emptyList():null)
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

	public static ByteBuffer newDirectBuffer(long addr, int cap, Object att) { return hlp.newDirectBuffer(addr, cap, att); }
	public static long getAddress(ByteBuffer buf) { return hlp.getAddress(buf); }
	public static void setBufferCapacityAndAddress(ByteBuffer buf, long addr, int cap) { assert buf.isDirect() : "buffer is not direct"; hlp.setCapacity(buf, cap); hlp.setAddress(buf, addr); }

	public static void freeDirectBuffer(Buffer buffer) {
		if (!buffer.isDirect()) return;

		// Java做了多次运行的处理，无须担心
		Object o = buffer;
		while (hlp.attachment(o) != null) o = hlp.attachment(o);

		Object cleaner = hlp.cleaner(o);
		if (cleaner != null) invokeClean(cleaner);
	}

	public static Object createCleaner(Object referent, Runnable fastCallable) { return hlp.newCleaner(referent, fastCallable); }
	public static void invokeClean(Object cleaner) { hlp.invokeClean(cleaner); }

	public static ByteBuffer newHeapBuffer(byte[] buf, int mark, int pos, int lim, int cap, int off) { return hlp.newHeapBuffer(buf, mark, pos, lim, cap, off); }
	public static byte[] getArray(ByteBuffer buf) { return hlp.getHb(buf); }
	public static int getOffset(ByteBuffer buf) { return hlp.getOffset(buf); }

	public static void reserveMemory(int length) throws OutOfMemoryError { hlp.reserveMemory(length, length); }
	public static void unreserveMemory(int length) { hlp.unreserveMemory(length, length); }

	public NativeMemory() { this(false); }
	public NativeMemory(long size) { this(false); allocate(size); }
	public NativeMemory(boolean zeroFilled) {
		unmanaged = new Unmanaged(zeroFilled);
		hlp.newCleaner(this, unmanaged);
	}

	private final Unmanaged unmanaged;
	private final ReentrantLock lock = new ReentrantLock();

	public long address() { return unmanaged.address(); }
	public long length() { return unmanaged.length; }
	public Lock lock() { return lock; }

	public long allocate(long cap) {
		lock.lock();
		try {
			return unmanaged.malloc(cap, false);
		} finally {
			lock.unlock();
		}
	}
	public boolean release() {
		lock.lock();
		try {
			return unmanaged.release();
		} finally {
			lock.unlock();
		}
	}
	public long resize(long cap) {
		lock.lock();
		try {
			return unmanaged.resize(cap);
		} finally {
			lock.unlock();
		}
	}

	static final class Unmanaged implements Runnable {
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

		boolean release() {
			if (base != 0) {
				U.freeMemory(base);
				hlp.unreserveMemory(length, except);
				base = 0;
				return true;
			}
			return false;
		}

		long resize(long cap) {
			long s0 = length;
			int s1 = except;
			long addr = malloc(cap, true);
			hlp.unreserveMemory(s0, s1);
			return addr;
		}

		long malloc(long cap, boolean resize) {
			if (cap < 0) throw new IllegalArgumentException("cap="+cap);
			if (base != 0 && !resize) release();

			this.except = (int) Math.min(cap, Integer.MAX_VALUE);

			boolean pa = hlp.isDirectMemoryPageAligned();
			int ps = hlp.pageSize();
			long size = Math.max(1L, cap + (pa ? ps : 0));
			hlp.reserveMemory(size, except);

			long base;
			try {
				base = this.base == 0 ? U.allocateMemory(size) : U.reallocateMemory(this.base, size);
			} catch (OutOfMemoryError x) {
				hlp.unreserveMemory(size, except);
				throw x;
			}

			if (size > length && clear) {
				U.setMemory(base + length, cap - length, (byte) 0);
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
			return addr;
		}

		@Override
		public void run() { release(); }
	}
}