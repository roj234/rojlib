package roj.lavac.expr;

import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodPoetL;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/1/31 0031 15:55
 */
public class New implements ASTNode {
	public New(IType type) {

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

	public void param(List<ASTNode> list) {

	}
}
