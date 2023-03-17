package roj.mildwind.parser.ast;

import roj.config.word.NotStatementException;
import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.type.JsBool;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.Type;

/**
 * @author Roj234
 * @since 2021/5/3 20:14
 */
final class AsBool implements Expression {
	Expression right;

	public AsBool(Expression right) { this.right = right; }

	@Override
	public void write(JsMethodWriter tree, boolean noRet) throws NotStatementException {
		right.write(tree, false);
		tree.invokeV("roj/mildwind/type/JsObject", "asBool", "()I");
		tree.invokeS("roj/mildwind/type/JsBool", "valueOf", "(I)Lroj/mildwind/type/JsObject;");
	}

	@Override
	public JsObject compute(JsObject ctx) { return JsBool.valueOf(right.compute(ctx).asBool()); }

	@Override
	public Type type() { return Type.BOOL; }
}
