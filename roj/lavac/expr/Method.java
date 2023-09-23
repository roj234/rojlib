package roj.lavac.expr;

import roj.asm.type.IType;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodWriterL;

import java.util.List;

/**
 * 操作符 - 调用方法
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public class Method implements Expression {
	Expression func;
	List<Expression> args;

	IType _type;

	public Method(Expression line, List<Expression> args) {
		// todo check map definition
		this.func = line;
		this.args = args;
	}

	@Override
	public void write(MethodWriterL cw, boolean noRet) throws NotStatementException {

	}

	@Override
	public IType type() {
		return _type == null ? func.type() : _type;
	}

	@Override
	public boolean equals(Object left) {
		return false;
	}
}
