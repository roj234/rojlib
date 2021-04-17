package roj.mildwind.parser.ast;

import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.Type;
import roj.text.CharList;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 操作符 - 逗号
 *
 * @author Roj233
 * @since 2020/10/13 22:17
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
	public void write(JsMethodWriter tree, boolean noRet) {
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
	public JsObject compute(JsObject ctx) {
		for (int i = 0; i < par.size()-1; i++) par.get(i).compute(ctx);
		return par.get(par.size()-1).compute(ctx);
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
