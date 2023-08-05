package roj.lavac.expr;

import roj.asm.type.Type;
import roj.compiler.ast.expr.ExprNode;
import roj.lavac.parser.JavaLexer;
import roj.lavac.parser.MethodWriterL;

/**
 * 临时操作符1 - 保存运算符
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class BinaryOpr implements ExprNode {
	final short op;

	static BinaryOpr get(short op) { return SYMBOLS[op-JavaLexer.logic_and]; }
	static final BinaryOpr[] SYMBOLS;
	static {
		int begin = JavaLexer.logic_and, end = JavaLexer.rsh_unsigned;
		SYMBOLS = new BinaryOpr[end - begin + 1];
		int j = 0;
		while (begin <= end) SYMBOLS[j++] = new BinaryOpr(begin++);
	}
	private BinaryOpr(int op) { this.op = (short) op; }

	@Override
	public void write(MethodWriterL cw, boolean noRet) { throw new UnsupportedOperationException(); }
	@Override
	public Type type() { return null; }
	@Override
	public boolean equalTo(Object left) { return this == left; }

	@Override
	public String toString() { return "<binary operator '"+JavaLexer.byId(op)+"';'>"; }
}