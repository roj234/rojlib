package roj.lavac.expr;

import roj.asm.type.Type;
import roj.asm.visitor.Label;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodWriterL;

import javax.annotation.Nonnull;

import static roj.lavac.parser.JavaLexer.logic_and;
import static roj.lavac.parser.JavaLexer.logic_or;

/**
 * 操作符 - 二元操作 a + b
 *
 * @author Roj233
 * @since 2022/2/24 19:56
 */
public class Binary implements Expression {
	final short operator;
	Expression left, right;
	Label target;

	Binary(short operator, Expression left, Expression right) {
		this.operator = operator;
		this.left = left;
		this.right = right;
	}

	public int constNum() {
		int a = (left = left.resolve()).isConstant() ? 1 : 0;
		if ((right = right.resolve()).isConstant()) a++;
		return a;
	}

	@Override
	public void write(MethodWriterL cw, boolean noRet) {
		if (noRet) {
			switch (operator) {
				case logic_or:
				case logic_and:
					break;
				default:
					throw new NotStatementException();
			}
		}

		switch (operator) {
			case logic_or:
			case logic_and:
				break;
			default:
				left.write(cw, false);
				right.write(cw, false);
		}

		writeOperator(cw);
	}

	void writeOperator(MethodWriterL tree) {

	}

	@Nonnull
	@Override
	public Expression resolve() {
		return this;
	}

	@Override
	public Type type() {
		return null;
	}

	@Override
	public String toString() {
		return "//";
	}

	@Override
	public boolean equals(Object left) {
		if (this == left) return true;
		if (!(left instanceof Binary)) return false;
		Binary b = (Binary) left;
		return b.left.equals(left) && b.right.equals(right) && b.operator == operator;
	}

	public void setTarget(Label ifFalse) {

	}
}
