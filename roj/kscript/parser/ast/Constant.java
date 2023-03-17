package roj.kscript.parser.ast;

import roj.concurrent.OperationDone;
import roj.config.word.NotStatementException;
import roj.config.word.Word;
import roj.kscript.asm.KS_ASM;
import roj.kscript.parser.JSLexer;
import roj.kscript.type.*;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * 操作符 - 常量表达式 1
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class Constant implements Expression {
	private final KType c;

	public Constant(KType number) {
		this.c = number;
	}

	public static Constant valueOf(int word) {
		return new Constant(KInt.valueOf(word));
	}

	public static Constant valueOf(double word) {
		return new Constant(KDouble.valueOf(word));
	}

	public static Constant valueOf(String word) {
		return new Constant(KString.valueOf(word));
	}

	public static Constant valueOf(boolean word) {
		return new Constant(KBool.valueOf(word));
	}

	public static Constant valueOf(KType word) {
		return new Constant(word);
	}

	public static Constant valueOf(Word word) {
		switch (word.type()) {
			case JSLexer.NULL:
				return valueOf(KNull.NULL);
			case JSLexer.UNDEFINED:
				return valueOf(KUndefined.UNDEFINED);
			case Word.CHARACTER:
			case Word.STRING:
				return valueOf(KString.valueOf(word.val()));
			case Word.DOUBLE:
			case Word.FLOAT:
				return valueOf(KDouble.valueOf(word.asDouble()));
			case Word.INTEGER:
				return valueOf(KInt.valueOf(word.asInt()));
			case JSLexer.TRUE:
			case JSLexer.FALSE:
				return valueOf(word.type() == JSLexer.TRUE ? KBool.TRUE : KBool.FALSE);
			case JSLexer.NAN:
				return valueOf(KDouble.valueOf(Double.NaN));
			case JSLexer.INFINITY:
				return valueOf(KDouble.valueOf(Double.POSITIVE_INFINITY));
			default:
				throw OperationDone.NEVER;
		}
	}

	@Override
	public boolean isConstant() {
		return true;
	}

	@Override
	public Constant asCst() {
		return this;
	}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof Constant)) return false;
		Constant cst = (Constant) left;
		return cst.c.getType() == c.getType() && cst.c.equalsTo(c);
	}

	public boolean asBool() {
		return c.asBool();
	}

	public int asInt() {
		return c.asInt();
	}

	public double asDouble() {
		return c.asDouble();
	}

	public String asString() {
		return c.asString();
	}

	@Override
	public void write(KS_ASM tree, boolean noRet) {
		if (noRet) throw new NotStatementException();

		tree.Load(c);
	}

	@Nonnull
	@Override
	public Expression compress() {
		return this;
	}

	@Override
	public KType compute(Map<String, KType> param) {
		return c;
	}

	@Override
	public byte type() {
		return typeOf(c);
	}

	public static byte typeOf(KType constant) {
		switch (constant.getType()) {
			case INT:
				return 0;
			case DOUBLE:
				return 1;
			case BOOL:
				return 3;
			case STRING:
				return 2;
			case NULL:
			case UNDEFINED:
				return -1;
		}
		throw new IllegalArgumentException("Unknown type of " + constant);
	}

	@Override
	public String toString() {
		return c.toString();
	}

	public KType val() {
		return c;
	}

}