package roj.compiler.ast.expr;

import roj.asm.type.IType;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;

/**
 * direct assign operator
 * @author Roj234
 * @since 2024/5/26 14:04
 */
final class AssignD extends Assign {
	AssignD(VarNode left, ExprNode right) {super(left, right);}

	@Override
	public String toString() { return left+" <<< "+right; }

	@Override
	public IType type() {return right.type();}

	@Override
	public ExprNode resolve(LocalContext ctx) {
		super.resolve(ctx);
		if (left.type().isPrimitive() || right.type().isPrimitive() || !cast.isNoop())
			ctx.report(Kind.ERROR, "assignD.error.cast", left, right);
		return this;
	}
}