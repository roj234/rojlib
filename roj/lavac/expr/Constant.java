package roj.lavac.expr;

import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.concurrent.OperationDone;
import roj.config.word.NotStatementException;
import roj.config.word.Word;
import roj.lavac.parser.JavaLexer;
import roj.lavac.parser.MethodWriterL;

/**
 * 操作符 - 常量
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
class Constant implements Expression {
	private final IType type;
	private final Object c;

	Constant(IType type, Object c) {
		this.type = type;
		this.c = c;
	}

	@Override
	public IType type() { return type; }
	@Override
	public boolean isConstant() { return true; }
	@Override
	public Object constVal() { return c; }

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
	public void write(MethodWriterL cw, boolean noRet) throws NotStatementException {

	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Expression)) return false;

		Expression r = (Expression) o;
		return r.isConstant() && type.equals(r.type()) && (c == null ? r.constVal() == null : type.equals(r.constVal()));
	}

	@Override
	public int hashCode() {
		int result = type.hashCode();
		result = 31 * result + c.hashCode();
		return result;
	}
}