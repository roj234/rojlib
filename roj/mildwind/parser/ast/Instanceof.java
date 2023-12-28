package roj.mildwind.parser.ast;

import roj.config.word.NotStatementException;
import roj.mildwind.JsContext;
import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.type.JsBool;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.Type;

/**
 * @author Roj234
 * @since 2023/6/19 0:04
 */
final class Instanceof implements Expression {
	Expression obj, cls;

	public Instanceof(Expression obj, Expression cls) {
		this.obj = obj;
		this.cls = cls;
	}

	@Override
	public void write(JsMethodWriter tree, boolean noRet) throws NotStatementException {
		obj.write(tree, false);
		cls.write(tree, false);
		tree.invokeV("roj/mildwind/type/JsObject", "op_instanceof", "(Lroj/mildwind/type/JsObject;)Z");
		tree.invokeS("roj/mildwind/type/JsBool", "valueOf", "(I)Lroj/mildwind/type/JsObject;");
	}

	@Override
	public JsObject compute(JsContext ctx) {
		JsObject n = obj.compute(ctx);
		JsObject h = cls.compute(ctx);
		return JsBool.valueOf(h.op_instanceof(n)?1:0);
	}

	@Override
	public Type type() { return Type.BOOL; }
}