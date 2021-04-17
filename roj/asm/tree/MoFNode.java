package roj.asm.tree;

import roj.asm.cst.ConstantPool;
import roj.util.DynByteBuf;

import javax.annotation.Nullable;

/**
 * @author Roj234
 * @since 2021/5/12 0:23
 */
public interface MoFNode extends Attributed {
	default void toByteArray(DynByteBuf w, ConstantPool pool) { throw new UnsupportedOperationException(getClass().getName() + " does not support encoding"); }

	String name();
	default void name(@Nullable ConstantPool cp, String name) { throw new UnsupportedOperationException(); }

	String rawDesc();
	default void rawDesc(@Nullable ConstantPool cp, String rawDesc) { throw new UnsupportedOperationException(); }
}
