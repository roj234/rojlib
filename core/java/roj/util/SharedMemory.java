package roj.util;

import roj.RojLib;

import java.io.Closeable;

/**
 * 共享内存
 * @author Roj234
 * @since 2024/4/24 2:42
 */
public class SharedMemory implements Closeable {
	private static native long nCreate(String name, long size) throws NativeException;
	private static native long nAttach(String name, boolean writable) throws NativeException;
	private static native long nGetAddress(long ptr);
	private static native void nClose(long ptr);

	private final String name;
	private final Releaser ref;

	public SharedMemory(String name) {
		if (!RojLib.hasNative(RojLib.SHARED_MEMORY)) throw new NativeException("平台不受支持");

		this.name = name;
		NativeMemory.createCleaner(this, ref = new Releaser());
	}

	public synchronized long create(long size) {
		ensureClosed();
		ref.ptr = nCreate(name, size);
		return nGetAddress(ref.ptr);
	}

	public synchronized long attach() {
		ensureClosed();
		ref.ptr = nAttach(name, true);
		if (ref.ptr == 0) throw new IllegalStateException("指定的共享内存["+name+"]不存在");
		return nGetAddress(ref.ptr);
	}

	private void ensureClosed() {
		if (ref.ptr != 0) throw new IllegalStateException("pipe is open");
	}

	public void close() {ref.run();}

	static final class Releaser implements Runnable {
		long ptr;

		@Override
		public void run() {nClose(ptr);}
	}
}