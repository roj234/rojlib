package roj.compiler.ast.expr;

import roj.compiler.asm.MethodWriter;

/**
 * @author Roj234
 * @since 2020/11/1 14:14
 */
public abstract class VarNode extends ExprNode {
	boolean isFinal() { return false; }
	abstract void preStore(MethodWriter cw);
	abstract void preLoadStore(MethodWriter cw);
	abstract void postStore(MethodWriter cw);
	abstract void copyValue(MethodWriter cw, boolean twoStack);
	boolean canBeReordered() { return false; }
}