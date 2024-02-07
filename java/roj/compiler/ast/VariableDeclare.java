package roj.compiler.ast;

import roj.asm.type.IType;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.UnresolvedExprNode;
import roj.compiler.context.CompileContext;
import roj.compiler.resolve.ResolveException;

/**
 * @author Roj234
 * @since 2024/2/21 0021 23:18
 */
public final class VariableDeclare implements UnresolvedExprNode {
	public final IType type;
	public final String name;
	public VariableDeclare(IType type, String name) {
		this.type = type;
		this.name = name;
	}

	@Override
	public IType type() { return type; }
	@Override
	public ExprNode resolve(CompileContext ctx) throws ResolveException { throw new ResolveException("not expression"); }
}