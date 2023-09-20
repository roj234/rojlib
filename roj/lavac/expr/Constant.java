package roj.lavac.expr;

import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.concurrent.OperationDone;
import roj.config.word.NotStatementException;
import roj.config.word.Word;
import roj.lavac.parser.JavaLexer;
import roj.lavac.parser.MethodPoetL;

/**
 * 操作符 - 常量
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class Constant implements Expression {
	private final Type type;
	private final Object c;

	Constant(Type type, Object c) {
		this.type = type;
		this.c = c;
	}

	public static Constant valueOf(boolean v) { return new Constant(Type.std(Type.BOOLEAN), v); }
	public static Constant valueOf(Object v) { return new Constant(TypeHelper.class2type(v.getClass()), v); }
	public static Constant valueOf(Word word) {
		switch (word.type()) {
			case JavaLexer.NULL: return valueOf((Object) null);
			case Word.CHARACTER: return valueOf(word.val().charAt(0));
			case Word.STRING: return valueOf(word.val());
			case Word.LONG: return valueOf(word.asLong());
			case Word.DOUBLE: return valueOf(word.asDouble());
			case Word.FLOAT: return valueOf((float)word.asDouble());
			case Word.INTEGER: return valueOf(word.asInt());
			case JavaLexer.TRUE: return valueOf(true);
			case JavaLexer.FALSE: return valueOf(false);
			//case JavaLexer.NAN: return valueOf(JsDouble.NAN);
			//case JavaLexer.INFINITY: return valueOf(JsDouble.INF);
			default: throw OperationDone.NEVER;
		}
	}

	@Override
	public void write(MethodPoetL tree, boolean noRet) throws NotStatementException {

	}

	@Override
	public Type type() {
		return type;
	}

	@Override
	public boolean isEqual(Expression left) {
		return left.isConstant()&& type.equals(left.type())&& c.equals(left.constVal());
	}
}