package roj.lavac.expr;

import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.ast.expr.ExprNode;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodWriterL;

import javax.annotation.Nonnull;

import static roj.lavac.parser.JavaLexer.*;

/**
 * @author Roj234
 * @since 2023/9/18 0018 7:53
 */
public class UnaryPre implements ExprNode {
	private final short op;
	ExprNode right;

	public UnaryPre(short op) { this.op = op; }

	@Override
	@SuppressWarnings("fallthrough")
	public IType type() {
		switch (op) {
			case logic_not: default: return Type.std(Type.BOOLEAN);
			case rev:
			case add: case sub:
			case inc: case dec: return right.type();
		}
	}

	@Override
	public void write(MethodWriterL cw, boolean noRet) {
		switch (op) {
			case inc: case dec: Assign.writeIncrement((LoadNode) right, cw, noRet, false, op == inc ? 1 : -1, right.type().rawType()); return;
		}

		if (noRet) throw new NotStatementException();

		right.write(cw, false);

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
	public final boolean isConstant() { return op != inc && op != dec && right.isConstant(); }
	@Override
	public final Object constVal() {
		ExprNode v = resolve();
		return v == this ? ExprNode.super.constVal() : v.constVal();
	}

	@Nonnull
	@Override
	public ExprNode resolve() {
		if (right == null) throw new IllegalArgumentException("Missing right");

		// todo.

		// inc/dec不能对常量使用, 所以不用管了
		if (!(right = right.resolve()).isConstant()) return this;

		// todo
		return this;
	}

	@Override
	public boolean equalTo(Object left) {
		if (this == left) return true;
		if (!(left instanceof UnaryPre)) return false;
		UnaryPre left1 = (UnaryPre) left;
		return left1.right.equalTo(right) && left1.op == op;
	}

	@Override
	public String toString() { return byId(op) + ' ' + right; }

	public String setRight(ExprNode right) {
		if (right == null) return "unary.missing_operand";

		if (!(right instanceof LoadNode)) {
			switch (op) {
				case inc: case dec: return "unary.expecting_variable";
			}
		}

		this.right = right;
		return null;
	}
}
