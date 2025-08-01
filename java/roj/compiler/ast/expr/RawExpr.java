package roj.compiler.ast.expr;

import roj.asm.type.IType;
import roj.compiler.CompileContext;
import roj.compiler.resolve.ResolveException;

/**
 * @author Roj234
 * @since 2024/2/8 1:06
 */
public interface RawExpr {
	IType type();
	Expr resolve(CompileContext ctx) throws ResolveException;

	default boolean isConstant() { return false; }
	default Object constVal() { throw new IllegalArgumentException("'"+this+"' ("+getClass().getName()+") is not a constant."); }
}