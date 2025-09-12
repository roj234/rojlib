package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.CompileContext;
import roj.compiler.JavaTokenizer;
import roj.compiler.asm.MethodWriter;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;

import static roj.compiler.JavaTokenizer.byId;

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
	public String toString() { return left+byId(op); }

	@Override
	public IType type() { return left.type(); }

	@Override
	public Expr resolve(CompileContext ctx) throws ResolveException {
		Expr node = left.resolve(ctx);
		left = node.asLeftValue(ctx);
		if (left == null) return NaE.resolveFailed(this);

		IType type = node.type();
		int actualType = type.getActualType();
		if (actualType == Type.CLASS) {
			actualType = TypeCast.getWrappedPrimitive(type);
			if (actualType == 0) {
				Expr override = ctx.getOperatorOverride(left, null, op | CompileContext.UNARY_POST);
				if (override != null) return override;

				ctx.report(this, Kind.ERROR, "op.notApplicable.unary", byId(op), type);
				return NaE.resolveFailed(this);
			}

			ctx.report(this, Kind.SEVERE_WARNING, "op.wrapper", type, byId(op));
		}
		return this;
	}

	@Override
	protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) { Assign.incOrDec(left, cw, cast == NORET, true, op == JavaTokenizer.inc ? 1 : -1); }

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