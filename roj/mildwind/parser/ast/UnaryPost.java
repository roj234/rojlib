package roj.mildwind.parser.ast;

import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.parser.JSLexer;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.Type;

/**
 * 一元运算符 i++ i--
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class UnaryPost implements Expression {
	private final short op;
	private final LoadExpression left;

	public UnaryPost(short op, Expression left) {
		if (op != JSLexer.inc && op != JSLexer.dec) throw new IllegalArgumentException("Unsupported operator " + op);

		this.op = op;
		this.left = (LoadExpression) left;
	}

	@Override
	public Type type() { return left.type() == Type.INT ? Type.INT : Type.DOUBLE; }

	@Override
	public void write(JsMethodWriter tree, boolean noRet) { Assign.writeIncrement(left, tree, noRet, true, op == JSLexer.inc ? 1 : -1, false); }

	@Override
	public JsObject compute(JsObject ctx) {
		int dt = op == JSLexer.inc ? 1 : -1;
		JsObject v = left.compute(ctx);
		v._ref();
		left.computeAssign(ctx, v.op_inc(dt));
		return v;
	}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof UnaryPost)) return false;
		UnaryPost right = (UnaryPost) left;
		return right.left.isEqual(left) && right.op == op;
	}

	@Override
	public String toString() { return left + JSLexer.byId(op); }
}
