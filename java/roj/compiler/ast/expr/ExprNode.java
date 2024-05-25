package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.Visitor;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;

/**
 * @author Roj233
 * @since 2022/2/24 19:16
 */
public abstract class ExprNode implements UnresolvedExprNode {
	protected int wordStart, wordEnd;

	public abstract String toString();

	public enum ExprKind {
		INVOKE_CONSTRUCTOR, IMMEDIATE_CONSTANT, LDC_CLASS
	}
	public boolean isKind(ExprKind kind) {return false;}
	public abstract IType type();
	public ExprNode resolve(LocalContext ctx) throws ResolveException { return this; }

	@Override
	public boolean isConstant() { return UnresolvedExprNode.super.isConstant(); }
	@Override
	public Object constVal() { return UnresolvedExprNode.super.constVal(); }

	public void visit(Visitor visitor, TypeCast.Cast exceptingType) {
		// NOT IMPLEMENTED
	}


	public abstract void write(MethodWriter cw, boolean noRet);
	public void writeDyn(MethodWriter cw, @Nullable TypeCast.Cast cast) {
		write(cw, false);
		if (cast != null) cast.write(cw);
	}
	protected static void mustBeStatement(boolean noRet) { if (noRet) LocalContext.get().report(Kind.ERROR, "expr.skipReturnValue"); }
}