package roj.asm.tree;

import roj.asm.type.Type;

/**
 * @author Roj233
 * @since 2022/4/28 20:55
 */
public interface FieldNode extends MoFNode {
	Type fieldType();
	default void fieldType(Type type) {
		throw new UnsupportedOperationException();
	}
}
