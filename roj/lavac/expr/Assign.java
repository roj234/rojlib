package roj.lavac.expr;

import roj.asm.type.Type;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Segment;
import roj.lavac.parser.MethodPoetL;

/**
 * @author Roj234
 * @since 2023/9/18 0018 9:07
 */
public class Assign extends Segment implements Expression {
	LoadExpression left;
	Expression right;


	static void writeIncrement(LoadExpression expr, MethodPoetL tree, boolean noRet, boolean returnBefore, Object data, Type dataType) {

	}


	public Assign(LoadExpression left, Expression right) {
		this.left = left;
		this.right = right;
	}

	public Type type() { return right.type(); }

	@Override
	protected boolean put(CodeWriter to) {
		left.resolve();
		return false;
	}

	@Override
	@SuppressWarnings("fallthrough")
	public void write(MethodPoetL tree, boolean noRet) {
		// todo
	}

	public Expression compress() {
		left = (LoadExpression) left.compress();
		right = right.compress();
		return this;
	}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof Assign)) return false;
		Assign assign = (Assign) left;
		return assign.left.isEqual(left) && assign.right.isEqual(right);
	}

	@Override
	public String toString() { return left+" = "+right; }
}
