package roj.lavac.expr;

import roj.asm.type.Type;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodPoetL;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 操作符 - 定义数组
 *
 * @author Roj233
 * @since 2022/2/27 19:43
 */
public final class ArrayDef implements Expression {
	Type type;
	List<Expression> expr;

	public ArrayDef(List<Expression> args) {
		this.type = type;
		this.expr = args;
	}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (left == null || getClass() != left.getClass()) return false;
		ArrayDef define = (ArrayDef) left;
		return arrayEq(expr, define.expr);
	}

	static boolean arrayEq(List<Expression> as, List<Expression> bs) {
		if (as.size() != bs.size()) return false;

		if (as.isEmpty()) return true;

		for (int i = 0; i < as.size(); i++) {
			Expression a = as.get(i);
			Expression b = bs.get(i);
			if (a == null) {
				if (b != null) return false;
			} else if (!a.isEqual(b)) return false;
		}
		return true;
	}

	@Nonnull
	@Override
	public Expression compress() {
		for (int i = 0; i < expr.size(); i++) {
			if (!expr.get(i).isConstant()) return this;
		}
		Object[] arrayEntry = new Object[expr.size()];
		for (int i = 0; i < expr.size(); i++) {
			arrayEntry[i] = expr.get(i).constVal();
		}
		return new Constant(type, arrayEntry);
	}

	@Override
	public Type type() {
		return type;
	}

	@Override
	public void write(MethodPoetL tree, boolean noRet) {
		if (noRet) throw new NotStatementException();

		tree.const1(expr.size()).newArray(type);
		for (int i = 0; i < expr.size(); i++) {
			Expression expr = this.expr.get(i);
			if (expr != null) {
				tree.dup().const1(i);
				expr.write(tree, false);
				tree.arrayStore();
			}
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder().append("new ").append(type).append("{");
		int i = 0;
		while (true) {
			sb.append(expr.get(i++).toString());
			if (i == expr.size()) break;
			sb.append(',');
		}
		return sb.append("}").toString();
	}
}
