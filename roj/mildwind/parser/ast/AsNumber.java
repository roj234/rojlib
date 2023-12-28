package roj.mildwind.parser.ast;

import roj.config.word.NotStatementException;
import roj.mildwind.JsContext;
import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.Type;

/**
 * @author Roj234
 * @since 2021/5/3 20:14
 */
final class AsNumber implements Expression {
	Expression right;

	public AsNumber(Expression right) { this.right = right; }

	@Override
	public void write(JsMethodWriter tree, boolean noRet) throws NotStatementException {
		right.write(tree, false);
		tree.ldc(0);
		tree.invokeV("roj/mildwind/type/JsObject", "op_inc", "(I)Lroj/mildwind/type/JsObject;");
	}

	public boolean isConstant() { return right.isConstant(); }
	public JsObject constVal() { return right.constVal().op_inc(0); }

	@Override
	public JsObject compute(JsContext ctx) { return right.compute(ctx).op_inc(0); }

	public Type type() { return isConstant() ? right.constVal().type() == Type.INT ? Type.INT : Type.DOUBLE : Type.OBJECT; }
}