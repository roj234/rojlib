package roj.lavac.expr;

import roj.asm.type.Type;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodWriterL;

/**
 * @author Roj234
 * @since 2023/1/30 0030 14:08
 */
public class This implements Expression {
	public static final This INST = new This();
	public This() {}

	@Override
	public void write(MethodWriterL cw, boolean noRet) throws NotStatementException {

	}

	@Override
	public Type type() {
		return null;
	}

	@Override
	public boolean equals(Object left) {
		return false;
	}
}
