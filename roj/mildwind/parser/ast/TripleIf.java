package roj.mildwind.parser.ast;

import roj.asm.Opcodes;
import roj.asm.visitor.Label;
import roj.mildwind.JsContext;
import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.Type;

import javax.annotation.Nonnull;

/**
 * 操作符 - 三元运算符 ? :
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class TripleIf implements Expression {
	Expression val, ok, fail;

	public TripleIf(Expression val, Expression ok, Expression fail) {
		this.val = val;
		this.ok = ok;
		this.fail = fail;
	}

	@Override
	public Type type() { return ok.type() == fail.type() ? ok.type() : Type.OBJECT; }

	@Override
	public void write(JsMethodWriter tree, boolean noRet) {
		Label ifFalse = new Label();
		Label end = new Label();

		val.write(tree, false);
		tree.invokeV("roj/mildwind/type/JsObject", "asBool", "()I");
		tree.jump(Opcodes.IFEQ, ifFalse);

		ok.write(tree, noRet);
		tree.jump(Opcodes.GOTO, end);

		tree.label(ifFalse);
		fail.write(tree, noRet);
		tree.label(end);
	}

	@Override
	public JsObject compute(JsContext ctx) { return val.compute(ctx).asBool() != 0 ? ok.compute(ctx) : fail.compute(ctx); }

	@Nonnull
	@Override
	public Expression compress() {
		ok = ok.compress();
		fail = fail.compress();
		if ((val = val.compress()).isConstant()) {
			return val.constVal().asBool() != 0 ? ok : fail;
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