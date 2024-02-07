package roj.compiler.ast.expr;

import roj.asm.tree.MethodNode;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.JavaLexer;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.CompileContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;

import static roj.compiler.JavaLexer.byId;

/**
 * @author Roj234
 * @since 2023/9/18 0018 9:06
 */
final class UnaryPost extends ExprNode {
	private final short op;
	private VarNode left;

	// inc(++) or dec(--)
	UnaryPost(short op, ExprNode left) {
		this.op = op;
		this.left = (VarNode) left;
	}

	@Override
	public String toString() { return left+JavaLexer.byId(op); }

	@Override
	public IType type() { return left.type(); }

	@Override
	public ExprNode resolve(CompileContext ctx) throws ResolveException {
		ExprNode node = left.resolve(ctx);
		if (node instanceof VarNode vn && !vn.isFinal()) left = (VarNode) node;
		else ctx.report(Kind.ERROR, "unary.error.final", node);

		IType type = node.type();
		int iType = type.getActualType();
		if (iType == Type.CLASS) {
			MethodNode override = ctx.getUnaryOverride(type, op, true);
			if (override != null) return Invoke.unaryAlt(left, override);

			iType = TypeCast.getWrappedPrimitive(type);
			if (iType == 0) {
				ctx.report(Kind.ERROR, "unary.error.notApplicable", byId(op), type);
				return this;
			}

			ctx.report(Kind.SEVERE_WARNING, "unary.warn.wrapper", type, byId(op));
		}
		return this;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) { Assign.incOrDec(left, cw, noRet, true, op == JavaLexer.inc ? 1 : -1); }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof UnaryPost right)) return false;
		return right.left.equals(left) && right.op == op;
	}

	@Override
	public int hashCode() {
		int result = op;
		result = 31 * result + left.hashCode();
		return result;
	}
}