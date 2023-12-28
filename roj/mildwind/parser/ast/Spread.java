package roj.mildwind.parser.ast;

import roj.config.word.NotStatementException;
import roj.mildwind.JsContext;
import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.type.JsArray;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.Type;

import javax.annotation.Nonnull;

/**
 * 扩展运算符
 *
 * @author solo6975
 * @since 2021/6/16 20:11
 */
final class Spread implements Expression {
	Expression provider;

	public Spread(Expression provider) { this.provider = provider; }

	@Override
	public Type type() { return Type.ARRAY; }

	@Override
	public void write(JsMethodWriter tree, boolean noRet) throws NotStatementException {
		assert !noRet;
		provider.write(tree, noRet);
	}

	@Nonnull
	@Override
	public Expression compress() {
		provider = provider.compress();
		return this;
	}

	private static JsArray __spread(JsObject val) {
		if (val.type() == Type.ARRAY) {
			return (JsArray) val;
		} else {
			JsArray arr = new JsArray();
			arr.pushAll(val);
			return arr;
		}
	}

	public boolean isConstant() { return provider.isConstant(); }
	public JsObject constVal() { return __spread(provider.constVal()); }

	public JsObject compute(JsContext ctx) { return provider.compute(ctx); }

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof Spread)) return false;
		Spread sp = (Spread) left;
		return sp.provider.isEqual(provider);
	}

	@Override
	public String toString() { return "... " + provider; }
}