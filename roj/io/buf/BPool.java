package roj.io.buf;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2023/2/8 0008 10:00
 */
public interface BPool {
	boolean allocate(boolean direct, int minCapacity, PooledBuffer callback);
	boolean reserve(DynByteBuf buf);
	default boolean expand(DynByteBuf buf, int more, boolean addAtEnd) { return false; }
}
