package roj.compiler.ast.expr;

import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.Tokens;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;

import static roj.compiler.Tokens.byId;

/**
 * @author Roj234
 * @since 2023/9/18 9:06
 */
final class PostfixOp extends Expr {
	private final short op;
	private LeftValue left;

	// inc(++) or dec(--)
	PostfixOp(short op, LeftValue left) {
		this.op = op;
		this.left = left;
	}

	@Override
	public String toString() { return left+ Tokens.byId(op); }

	@Override
	public IType type() { return left.type(); }

	@Override
	public Expr resolve(LocalContext ctx) throws ResolveException {
		Expr node = left.resolve(ctx);
		left = node.asLeftValue(ctx);
		if (left == null) return NaE.RESOLVE_FAILED;

		IType type = node.type();
		int iType = type.getActualType();
		if (iType == Type.CLASS) {
			Expr override = ctx.getOperatorOverride(left, null, op | LocalContext.UNARY_POST);
			if (override != null) return override;

			iType = TypeCast.getWrappedPrimitive(type);
			if (iType == 0) {
				ctx.report(this, Kind.ERROR, "op.notApplicable.unary", byId(op), type);
				return NaE.RESOLVE_FAILED;
			}

			ctx.report(this, Kind.SEVERE_WARNING, "unary.warn.wrapper", type, byId(op));
		}
		return this;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) { Assign.incOrDec(left, cw, noRet, true, op == Tokens.inc ? 1 : -1); }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof PostfixOp right)) return false;
		return right.left.equals(left) && right.op == op;
	}

	@Override
	public int hashCode() {
		int result = op;
		result = 31 * result + left.hashCode();
		return result;
	}
}