package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.compiler.CompileContext;
import roj.compiler.asm.MethodWriter;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.TypeCast;
import roj.text.TextUtil;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/9/18 9:07
 */
final class SequenceExpr extends Expr {
	private final List<Expr> sequence;

	public SequenceExpr(List<Expr> sequence) { this.sequence = sequence; }

	@Override
	public String toString() { return TextUtil.join(sequence, ", "); }

	@Override
	public IType type() { return sequence.get(sequence.size()-1).type(); }

	@NotNull
	@Override
	public Expr resolve(CompileContext ctx) {
		int i = 0;
		while (i < sequence.size()) {
			Expr node = sequence.get(i).resolve(ctx);
			if (node.isConstant() && i != sequence.size()-1) {
				ctx.report(this, Kind.SEVERE_WARNING, "chained.warn.constant_expr");
				sequence.remove(i);
			} else {
				sequence.set(i++, node);
			}
		}
		return sequence.size() == 1 ? sequence.get(0) : this;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		int t = w(cw);
		sequence.get(t).write(cw, false);
	}
	@Override
	public void write(MethodWriter cw, @Nullable TypeCast.Cast returnType) {
		int t = w(cw);
		sequence.get(t).write(cw, returnType);
	}
	private int w(MethodWriter cw) {
		int t = sequence.size()-1;
		for (int i = 0; i < t; i++) sequence.get(i).write(cw, true);
		return t;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SequenceExpr that = (SequenceExpr) o;

		return sequence.equals(that.sequence);
	}

	@Override
	public int hashCode() {
		return sequence.hashCode();
	}
}