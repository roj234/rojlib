package roj.mildwind.parser.ast;

import roj.asm.Opcodes;
import roj.config.word.NotStatementException;
import roj.mildwind.JsContext;
import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.parser.ParseContext;
import roj.mildwind.type.JsNull;
import roj.mildwind.type.JsObject;
import roj.util.Helpers;

/**
 * 操作符 - 简单操作(this, arguments)
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class Std implements Expression {
	public static final Std THIS = new Std(1), ARGUMENTS = new Std(2);

	final byte type;
	public Std(int type) { this.type = (byte) type; }

	@Override
	public void write(JsMethodWriter tree, boolean noRet) {
		if (noRet) throw new NotStatementException();
		tree.vars(Opcodes.ALOAD, type);
	}

	@Override
	public JsObject compute(JsContext ctx) {
		if (type == 1) return ctx.root;
		return JsNull.UNDEFINED;
	}

	@Override
	public void var_op(ParseContext ctx, int op_type) {
		if (op_type == 2) Helpers.athrow(ctx.lex().err("write_to_native_variable"));
	}

	@Override
	public String toString() { return type == 1 ? "this" : "arguments"; }
}