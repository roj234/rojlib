package roj.lavac.expr;

import roj.asm.type.IType;
import roj.lavac.parser.JavaLexer;
import roj.lavac.parser.MethodWriterL;

/**
 * @author Roj234
 * @since 2023/9/18 0018 9:06
 */
final class UnaryPost implements Expression {
	private final short op;
	private final LoadExpression left;

	UnaryPost(short op, Expression left) {
		this.op = op;
		this.left = (LoadExpression) left;
	}

	@Override
	public IType type() { return left.type(); }

	@Override
	public void write(MethodWriterL cw, boolean noRet) {
		Assign.writeIncrement(left, cw, noRet, true, op == JavaLexer.inc ? 1 : -1, left.type().rawType());
	}

	@Override
	public String toString() { return left + JavaLexer.byId(op); }

	@Override
	public boolean equals(Object left) {
		if (this == left) return true;
		if (!(left instanceof UnaryPost)) return false;
		UnaryPost right = (UnaryPost) left;
		return right.left.equals(left) && right.op == op;
	}

	@Override
	public int hashCode() {
		int result = op;
		result = 31 * result + left.hashCode();
		return result;
	}
}
