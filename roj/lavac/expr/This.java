package roj.lavac.expr;

import roj.asm.type.Type;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodPoetL;

/**
 * @author Roj234
 * @since 2023/1/30 0030 14:08
 */
public class This implements ASTNode {
	public This() {
		
	}

	@Override
	public void write(MethodPoetL tree, boolean noRet) throws NotStatementException {

	}

	@Override
	public Type type() {
		return null;
	}

	@Override
	public boolean isEqual(ASTNode left) {
		return false;
	}
}
