package roj.compiler.ast.expr;

import roj.compiler.asm.MethodWriter;

/**
 * @author Roj234
 * @since 2020/11/1 14:14
 */
public interface VarNode extends ExprNode {
	default boolean isFinal() { return false; }
	void preStore(MethodWriter cw);
	void preLoadStore(MethodWriter cw);
	void postStore(MethodWriter cw);
	void copyValue(MethodWriter cw, boolean twoStack);
	default boolean canBeReordered() { return false; }
}