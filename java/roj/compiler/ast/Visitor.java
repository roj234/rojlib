package roj.compiler.ast;

import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.ast.expr.ExprNode;

/**
 * @author Roj234
 * @since 2024/2/14 0014 10:56
 */
public abstract class Visitor {
	public void visitThis() {}
	public void visitSuper() {}
	public void visitEncloseClass(boolean ThisEnclosing, Type type) {}
	public void visitArrayGet(ExprNode array, ExprNode index) {}

	public void visitUnaryPre(short type) {}
	public void visitCast(IType type) {}
}