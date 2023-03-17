package roj.io.buf;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2023/2/8 0008 10:00
 */
public interface BPool {
	DynByteBuf allocate(boolean direct, int cap);
	void reserve(DynByteBuf buf);
	boolean isPooled(DynByteBuf buf);
	default boolean expand(DynByteBuf buf, int more, boolean addAtEnd) {
		return false;
	}
}
