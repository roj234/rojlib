package roj.asm.tree;

import roj.asm.cp.ConstantPool;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/12 0:23
 */
public interface RawNode extends Attributed {
	default void toByteArray(DynByteBuf w, ConstantPool pool) { throw new UnsupportedOperationException(getClass().getName() + " does not support encoding"); }

	String name();
	default void name(String name) { throw new UnsupportedOperationException(); }

	String rawDesc();
	default void rawDesc(String rawDesc) { throw new UnsupportedOperationException(); }
}