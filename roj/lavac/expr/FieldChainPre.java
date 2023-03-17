package roj.lavac.expr;

import roj.asm.type.Type;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodPoetL;
import roj.text.TextUtil;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/1/30 0030 14:11
 */
public class FieldChainPre implements ASTNode {
	List<String> chain;

	public FieldChainPre(CharSequence seq) {
		chain = TextUtil.split(seq, '/');
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

	public ASTNode ThisFirst() {
		return this;
	}
}
