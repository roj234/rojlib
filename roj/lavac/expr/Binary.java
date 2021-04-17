package roj.lavac.expr;

import roj.asm.tree.insn.LabelInsnNode;
import roj.asm.type.Type;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodPoetL;

import javax.annotation.Nonnull;

import static roj.lavac.parser.JavaLexer.*;

/**
 * 操作符 - 二元操作 a + b
 *
 * @author Roj233
 * @since 2022/2/24 19:56
 */
public final class Binary implements ASTNode {
	final short operator;
	ASTNode left, right;
	LabelInsnNode target;

	public Binary(short operator, ASTNode left, ASTNode right) {
		switch (operator) {
			default:
				throw new IllegalArgumentException("Unsupported operator " + byId(operator));
			case pow:
			case add:
			case and:
			case div:
			case lsh:
			case mod:
			case mul:
			case or:
			case rsh:
			case rsh_unsigned:
			case sub:
			case xor:
			case logic_and:
			case logic_or:
			case lss:
			case gtr:
			case geq:
			case leq:
			case equ:
			case neq:
				break;
		}
		this.operator = operator;
		this.left = left;
		this.right = right;
	}

	public int constNum() {
		int a = (left = left.compress()).isConstant() ? 1 : 0;
		if ((right = right.compress()).isConstant()) a++;
		return a;
	}

	@Override
	public void write(MethodPoetL tree, boolean noRet) {
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
				left.write(tree, false);
				right.write(tree, false);
		}

		writeOperator(tree);
	}

	void writeOperator(MethodPoetL tree) {

	}

	@Nonnull
	@Override
	public ASTNode compress() {
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
	public boolean isEqual(ASTNode left) {
		if (this == left) return true;
		if (!(left instanceof Binary)) return false;
		Binary b = (Binary) left;
		return b.left.isEqual(left) && b.right.isEqual(right) && b.operator == operator;
	}

	public void setTarget(LabelInsnNode ifFalse) {

	}
}
