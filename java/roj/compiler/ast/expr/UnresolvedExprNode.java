package roj.compiler.ast.expr;

import roj.asm.type.IType;
import roj.compiler.context.CompileContext;
import roj.compiler.resolve.ResolveException;

/**
 * @author Roj234
 * @since 2024/2/8 0008 1:06
 */
public interface UnresolvedExprNode {
	IType type();
	ExprNode resolve(CompileContext ctx) throws ResolveException;

	default boolean isConstant() { return false; }
	default Object constVal() { throw new IllegalArgumentException("'"+this+"' ("+getClass().getName()+") is not a constant."); }
}