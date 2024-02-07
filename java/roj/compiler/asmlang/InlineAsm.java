package roj.compiler.asmlang;

import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.ExprNode;

/**
 * @author Roj234
 * @since 2024/2/6 0006 12:47
 */
public class InlineAsm extends ExprNode {
	public InlineAsm(String str) {

	}

	@Override
	public String toString() {
		return "";
	}

	@Override
	public IType type() {
		return null;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {

	}
}