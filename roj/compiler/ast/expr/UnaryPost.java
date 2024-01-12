package roj.compiler.ast.expr;

import roj.asm.tree.MethodNode;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.JavaLexer;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.CompileContext;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;

import javax.tools.Diagnostic;

import static roj.compiler.JavaLexer.byId;

/**
 * @author Roj234
 * @since 2023/9/18 0018 9:06
 */
final class UnaryPost implements ExprNode {
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
		else ctx.report(Diagnostic.Kind.ERROR, "unary.error.final", node);

		IType type = node.type();
		int iType = type.getActualType();
		if (iType == Type.CLASS) {
			MethodNode override = ctx.getUnaryOverride(type.rawType(), op, true);
			if (override != null) return Invoke.unaryAlt(left, override);

			iType = TypeCast.getWrappedPrimitive(type.rawType());
			if (iType == 0) {
				ctx.report(Diagnostic.Kind.ERROR, "unary.error.not_applicable", byId(op), type);
				return this;
			}

			ctx.report(Diagnostic.Kind.MANDATORY_WARNING, "unary.warn.wrapper_inc", type, byId(op));
		}
		return this;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) { Assign.incOrDec(left, cw, noRet, true, op == JavaLexer.inc ? 1 : -1); }

	@Override
	public boolean equalTo(Object o) {
		if (this == o) return true;
		if (!(o instanceof UnaryPost)) return false;
		UnaryPost right = (UnaryPost) o;
		return right.left.equals(o) && right.op == op;
	}
}