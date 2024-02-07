package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.Visitor;
import roj.compiler.context.CompileContext;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;

/**
 * @author Roj233
 * @since 2022/2/24 19:16
 */
public abstract class ExprNode implements UnresolvedExprNode {
	protected int wordStart, wordEnd;

	public abstract String toString();

	public abstract IType type();
	public ExprNode resolve(CompileContext ctx) throws ResolveException { return this; }

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
	protected static void mustBeStatement(boolean noRet) { if (noRet) throw new ResolveException("not_statement"); }
}