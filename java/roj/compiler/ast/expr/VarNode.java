package roj.compiler.ast.expr;

import roj.compiler.asm.MethodWriter;

/**
 * @author Roj234
 * @since 2020/11/1 14:14
 */
public abstract class VarNode extends ExprNode {
	protected VarNode() {}
	protected VarNode(int _noUpdate) {}

	boolean isFinal() { return false; }
	protected abstract void preStore(MethodWriter cw);
	protected abstract void preLoadStore(MethodWriter cw);
	protected abstract void postStore(MethodWriter cw, int state);
	protected abstract int copyValue(MethodWriter cw, boolean twoStack);
	boolean canBeReordered() { return false; }
}