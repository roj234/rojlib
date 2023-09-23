package roj.lavac.expr;

import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.lavac.parser.MethodWriterL;

/**
 * @author Roj234
 * @since 2023/9/18 0018 9:07
 */
class Assign implements Expression {
	private LoadExpression left;
	private Expression right;

	Assign(LoadExpression left, Expression right) {
		this.left = left;
		this.right = right;
	}

	// javac is left.type()
	// I fixed this annoying 'feature'
	public IType type() { return right.type(); }

	public Expression resolve() {
		left = (LoadExpression) left.resolve();
		right = right.resolve();
		return this;
	}

	@Override
	@SuppressWarnings("fallthrough")
	public void write(MethodWriterL cw, boolean noRet) {
		// todo
	}

	static void writeIncrement(LoadExpression expr, MethodWriterL tree, boolean noRet, boolean returnBefore, Object data, Type dataType) {

	}

	@Override
	public String toString() { return left+" = "+right; }

	@Override
	public boolean equals(Object left) {
		if (this == left) return true;
		if (!(left instanceof Assign)) return false;
		Assign assign = (Assign) left;
		return assign.left.equals(left) && assign.right.equals(right);
	}

	@Override
	public int hashCode() {
		int result = left.hashCode();
		result = 31 * result + right.hashCode();
		return result;
	}
}
