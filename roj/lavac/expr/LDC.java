package roj.lavac.expr;

import roj.asm.tree.anno.AnnVal;
import roj.asm.type.Type;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodPoetL;

/**
 * @author Roj234
 * @since 2023/1/29 0029 23:51
 */
public class LDC implements ASTNode {
	public static final Type ANY_TYPE = new Type(null);

	public static LDC Int(int v) {
		return null;
	}

	public static LDC Char(char v) {
		return null;
	}

	public static LDC String(String v) {
		return null;
	}

	public static LDC Double(double v) {
		return null;
	}

	public static LDC Float(double v) {
		return null;
	}

	public static LDC True() {
		return null;
	}

	public static LDC Null() {
		return null;
	}

	public static LDC False() {
		return null;
	}

	public LDC(AnnVal val) {

	}

	public AnnVal val() {
		return null;
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
