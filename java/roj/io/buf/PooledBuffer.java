package roj.io.buf;

import roj.util.NativeMemory;

/**
 * @author Roj234
 * @since 2023/5/16 7:25
 */
interface PooledBuffer {
	default Object set(NativeMemory ref, long address, int length) { throw new UnsupportedOperationException("not direct buffer"); }
	default Object set(byte[] array, int offset, int length) { throw new UnsupportedOperationException("not heap buffer"); }

	Object pool(Object newPool);

	Bitmap page();
	void page(Bitmap p);

	int getKeepBefore();
	void setKeepBefore(int keepBefore);

	void _expand(int len, boolean backward);

	default void addRef() {addRef(1);}
	void addRef(int count);
	void release();
}
