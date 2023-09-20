package roj.lavac.expr;

import roj.asm.type.Type;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodPoetL;

import javax.annotation.Nonnull;

import static roj.lavac.parser.JavaLexer.*;

/**
 * @author Roj234
 * @since 2023/9/18 0018 7:53
 */
public class UnaryPre implements Expression {
	private final short op;
	private Expression right;

	public UnaryPre(short op) {
		switch (op) {
			default: throw new IllegalArgumentException("Unsupported operator " + op);
			case logic_not:
			case rev:
			case inc: case dec:
			case add: case sub:
		}
		this.op = op;
	}

	@Override
	@SuppressWarnings("fallthrough")
	public Type type() {
		switch (op) {
			case logic_not: default: return Type.std(Type.BOOLEAN);
			case rev:
			case add: case sub:
			case inc: case dec: return right.type();
		}
	}

	@Override
	public void write(MethodPoetL tree, boolean noRet) {
		switch (op) {
			case inc: case dec: Assign.writeIncrement((LoadExpression) right, tree, noRet, false, op == inc ? 1 : -1, right.type()); return;
		}

		if (noRet) throw new NotStatementException();

		right.write(tree, false);

		switch (op) {
			case logic_not: default:

				break;
			case rev:
				break;
			case add:

				break;
			case sub:
				break;
		}
	}

	@Override
	public boolean isConstant() { return op != inc && op != dec && right.isConstant(); }
	@Override
	public Object constVal() {
		Expression v = compress();
		return v == this ? Expression.super.constVal() : v.constVal();
	}

	@Nonnull
	@Override
	public Expression compress() {
		if (right == null) throw new IllegalArgumentException("Missing right");

		// todo.

		// inc/dec不能对常量使用, 所以不用管了
		if (!(right = right.compress()).isConstant()) return this;

		// todo
		return this;
	}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof UnaryPre)) return false;
		UnaryPre left1 = (UnaryPre) left;
		return left1.right.isEqual(right) && left1.op == op;
	}

	@Override
	public String toString() { return byId(op) + ' ' + right; }

	public String setRight(Expression right) {
		if (right == null) return "upf: right is null";

		if (!(right instanceof LoadExpression)) {
			switch (op) {
				case inc: case dec: return "unary.expecting_variable";
			}
		}

		this.right = right;
		return null;
	}
}
