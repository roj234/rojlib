package roj.lavac.expr;

import roj.asm.type.Type;
import roj.lavac.parser.JavaLexer;
import roj.lavac.parser.MethodPoetL;

/**
 * @author Roj234
 * @since 2023/9/18 0018 9:06
 */
public class UnaryPost implements Expression {
	private final short op;
	private final LoadExpression left;

	public UnaryPost(short op, Expression left) {
		if (op != JavaLexer.inc && op != JavaLexer.dec) throw new IllegalArgumentException("Unsupported operator " + op);

		this.op = op;
		this.left = (LoadExpression) left;
	}

	@Override
	public Type type() { return left.type(); }

	@Override
	public void write(MethodPoetL tree, boolean noRet) { Assign.writeIncrement(left, tree, noRet, true, op == JavaLexer.inc ? 1 : -1, left.type()); }

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof UnaryPost)) return false;
		UnaryPost right = (UnaryPost) left;
		return right.left.isEqual(left) && right.op == op;
	}

	@Override
	public String toString() { return left + JavaLexer.byId(op); }
}
