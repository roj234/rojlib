package roj.lavac.expr;

import roj.asm.type.Type;
import roj.lavac.parser.MethodPoetL;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2023/9/18 0018 9:06
 */
public class TripleIf implements Expression {
	Expression val, ok, fail;

	public TripleIf(Expression val, Expression ok, Expression fail) {
		this.val = val;
		this.ok = ok;
		this.fail = fail;
	}

	@Override
	public Type type() { return ok.type() == fail.type() ? ok.type() : null; } // todo common super class or Object

	@Override
	public void write(MethodPoetL tree, boolean noRet) {

	}

	@Nonnull
	@Override
	public Expression compress() {
		ok = ok.compress();
		fail = fail.compress();
		if ((val = val.compress()).isConstant()) {
			//return val.constVal().asBool() != 0 ? ok : fail;
		}
		return this;
	}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof TripleIf)) return false;
		TripleIf tripleIf = (TripleIf) left;
		return tripleIf.val.isEqual(val) && tripleIf.ok.isEqual(ok) && tripleIf.fail.isEqual(fail);
	}

	@Override
	public String toString() { return val.toString() + " ? " + ok + " : " + fail; }
}
