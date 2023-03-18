package roj.mildwind.parser.ast;

import roj.asm.Opcodes;
import roj.config.word.NotStatementException;
import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.type.JsBool;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.Type;

/**
 * @author Roj234
 * @since 2023/6/19 0:04
 */
final class In implements Expression {
	Expression needle, haystack;

	public In(Expression needle, Expression haystack) {
		this.needle = needle;
		this.haystack = haystack;
	}

	@Override
	public void write(JsMethodWriter tree, boolean noRet) throws NotStatementException {
		needle.write(tree, false);
		haystack.write(tree, false);
		tree.one(Opcodes.SWAP);
		tree.invokeV("roj/mildwind/type/JsObject", "op_in", "(Lroj/mildwind/type/JsObject;)Z");
		tree.invokeS("roj/mildwind/type/JsBool", "valueOf", "(I)Lroj/mildwind/type/JsObject;");
	}

	@Override
	public JsObject compute(JsObject ctx) {
		JsObject n = needle.compute(ctx);
		JsObject h = haystack.compute(ctx);
		return JsBool.valueOf(h.op_in(n)?1:0);
	}

	@Override
	public Type type() { return Type.BOOL; }
}
