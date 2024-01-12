package roj.compiler.asmlang;

import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.ExprNode;
import roj.config.word.NotStatementException;

/**
 * @author Roj234
 * @since 2024/2/6 0006 12:47
 */
public class InlineAsm implements ExprNode {
	public InlineAsm(String str) {

	}

	@Override
	public IType type() {
		return null;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) throws NotStatementException {

	}

	@Override
	public boolean equalTo(Object o) {
		return o == this;
	}
}