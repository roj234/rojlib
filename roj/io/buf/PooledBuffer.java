package roj.io.buf;

import roj.util.NativeMemory;

/**
 * @author Roj234
 * @since 2023/5/16 0016 7:25
 */
public interface PooledBuffer {
	boolean isDirect();

	default Object set(NativeMemory ref, long address, int length) { throw new UnsupportedOperationException("not direct buffer"); }
	default Object set(byte[] array, int offset, int length) { throw new UnsupportedOperationException("not heap buffer"); }

	BPool pool();
	void pool(BPool pool);
	void close();

	void release();
}
