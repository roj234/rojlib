package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.TypeCast;
import roj.text.TextUtil;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/9/18 0018 9:07
 */
final class Chained extends ExprNode {
	private final List<ExprNode> exprList;

	public Chained(List<ExprNode> exprList) { this.exprList = exprList; }

	@Override
	public String toString() { return TextUtil.join(exprList, ", "); }

	@Override
	public IType type() { return exprList.get(exprList.size()-1).type(); }

	@NotNull
	@Override
	public ExprNode resolve(LocalContext ctx) {
		int i = 0;
		while (i < exprList.size()) {
			ExprNode node = exprList.get(i).resolve(ctx);
			if (node.isConstant() && i != exprList.size()-1) {
				ctx.report(this, Kind.SEVERE_WARNING, "chained.warn.constant_expr");
				exprList.remove(i);
			} else {
				exprList.set(i++, node);
			}
		}
		return exprList.size() == 1 ? exprList.get(0) : this;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		int t = w(cw);
		exprList.get(t).write(cw, false);
	}
	@Override
	public void write(MethodWriter cw, @Nullable TypeCast.Cast returnType) {
		int t = w(cw);
		exprList.get(t).write(cw, returnType);
	}
	private int w(MethodWriter cw) {
		int t = exprList.size()-1;
		for (int i = 0; i < t; i++) exprList.get(i).write(cw, true);
		return t;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Chained)) return false;
		Chained method = (Chained) o;
		return exprList.equals(method.exprList);
	}

	@Override
	public int hashCode() {
		return exprList.hashCode();
	}
}