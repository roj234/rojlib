package roj.mildwind.parser.ast;

import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.parser.JSLexer;

/**
 * 临时操作符1 - 保存运算符
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class BinaryOpr implements Expression {
	final short op;

	static BinaryOpr get(short op) { return SYMBOLS[op-JSLexer.logic_and]; }
	static final BinaryOpr[] SYMBOLS;
	static {
		int begin = JSLexer.logic_and, end = JSLexer.optional_chaining;
		SYMBOLS = new BinaryOpr[end - begin + 1];
		int j = 0;
		while (begin <= end) SYMBOLS[j++] = new BinaryOpr(begin++);
	}
	private BinaryOpr(int op) { this.op = (short) op; }

	@Override
	public void write(JsMethodWriter tree, boolean noRet) { throw new UnsupportedOperationException(); }

	@Override
	public String toString() { return "<binary operator '"+JSLexer.byId(op)+"';'>"; }
}