package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.collect.SimpleList;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.TypeCast;
import roj.text.TextUtil;

/**
 * @author Roj234
 * @since 2023/9/18 0018 9:07
 */
final class Chained extends ExprNode {
	public final SimpleList<ExprNode> par;

	public Chained(SimpleList<ExprNode> par) { this.par = par; }

	@Override
	public String toString() { return TextUtil.join(par, ", "); }

	@Override
	public IType type() { return par.get(par.size()-1).type(); }

	@NotNull
	@Override
	public ExprNode resolve(LocalContext ctx) {
		for (int i = 0; i < par.size(); i++) {
			ExprNode node = par.get(i).resolve(ctx);
			if (node.isConstant()) {
				ctx.report(Kind.SEVERE_WARNING, "chained.warn.constant_expr");
				par.remove(i--);
			} else par.set(i, node);
		}
		par.set(par.size()-1, par.get(par.size()-1).resolve(ctx));
		return par.size() == 1 ? par.get(0) : this;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		int t = w(cw);
		par.get(t).write(cw, false);
	}
	@Override
	public void writeDyn(MethodWriter cw, @Nullable TypeCast.Cast cast) {
		int t = w(cw);
		par.get(t).writeDyn(cw, cast);
	}
	private int w(MethodWriter cw) {
		int t = par.size()-1;
		for (int i = 0; i < t; i++) par.get(i).write(cw, true);
		return t;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Chained)) return false;
		Chained method = (Chained) o;
		return par.equals(method.par);
	}

	@Override
	public int hashCode() {
		return par.hashCode();
	}
}