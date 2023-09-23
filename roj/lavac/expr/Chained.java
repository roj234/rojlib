package roj.lavac.expr;

import roj.asm.type.IType;
import roj.lavac.parser.MethodWriterL;
import roj.text.CharList;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/9/18 0018 9:07
 */
final class Chained implements Expression {
	public final List<Expression> par;

	public Chained() { par = new ArrayList<>(); }

	@Override
	public IType type() { return par.get(par.size()-1).type(); }

	@Nonnull
	@Override
	public Expression resolve() {
		for (int i = 0; i < par.size(); i++) par.set(i, par.get(i).resolve());
		return par.size() == 1 ? par.get(0) : this;
	}

	@Override
	public void write(MethodWriterL cw, boolean noRet) {
		int t = par.size()-1;
		Expression last = par.get(t);
		for (int i = 0; i < t; i++) par.get(i).write(cw, true);
		last.write(cw, false);
	}

	public void append(Expression expr) { par.add(expr); }

	@Override
	public String toString() {
		CharList sb = new CharList();
		for (int i = 0; i < par.size(); i++) {
			sb.append(par.get(i).toString()).append(", ");
		}
		sb.setLength(sb.length()-2);
		return sb.toStringAndFree();
	}

	@Override
	public boolean equals(Object left) {
		if (this == left) return true;
		if (!(left instanceof Chained)) return false;
		Chained method = (Chained) left;
		return par.equals(method.par);
	}

	@Override
	public int hashCode() {
		return par.hashCode();
	}
}
