package roj.lavac.expr;

import roj.asm.type.Type;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodPoetL;

import java.util.List;

/**
 * 操作符 - 调用方法
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class Method implements Expression {
	Expression func;
	List<Expression> args;

	int flag;
	static final byte NEW = 1, DYNAMIC = 2, SPREAD = 4;

	public Method(Expression line, List<Expression> args, boolean isNew) {
		this.func = line;
		this.args = args;
		this.flag = isNew ? NEW : 0;
	}

	@Override
	public void write(MethodPoetL tree, boolean noRet) throws NotStatementException {

	}

	@Override
	public Type type() {
		return null;
	}

	@Override
	public boolean isEqual(Expression left) {
		return false;
	}
}
