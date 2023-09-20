package roj.lavac.expr;

import roj.asm.type.Type;
import roj.lavac.parser.MethodPoetL;
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
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof Chained)) return false;
		Chained method = (Chained) left;
		return ArrayDef.arrayEq(par, method.par);
	}

	@Override
	public void write(MethodPoetL tree, boolean noRet) {
		int t;
		Expression last = par.get(t = par.size() - 1);
		for (int i = 0; i < t; i++) {
			par.get(i).write(tree, true);
		}
		last.write(tree, false);
	}

	@Nonnull
	@Override
	public Expression compress() {
		for (int i = 0; i < par.size(); i++) {
			par.set(i, par.get(i).compress());
		}
		return par.size() == 1 ? par.get(0) : this;
	}

	@Override
	public Type type() { return par.get(par.size()-1).type(); }

	@Override
	public String toString() {
		CharList sb = new CharList();
		for (int i = 0; i < par.size(); i++) {
			sb.append(par.get(i).toString()).append(", ");
		}
		sb.setLength(sb.length()-2);
		return sb.toStringAndFree();
	}

	public void append(Expression expr) { par.add(expr); }
}
