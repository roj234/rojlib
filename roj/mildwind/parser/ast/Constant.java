package roj.mildwind.parser.ast;

import roj.asm.Opcodes;
import roj.concurrent.OperationDone;
import roj.config.word.NotStatementException;
import roj.config.word.Word;
import roj.mildwind.JsContext;
import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.parser.JSLexer;
import roj.mildwind.type.*;

/**
 * 操作符 - 常量
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class Constant implements Expression {
	private final JsObject c;

	private Constant(JsObject c) { this.c = c; }

	public static Constant valueOf(boolean v) { return new Constant(v ? JsBool.TRUE : JsBool.FALSE); }
	public static Constant valueOf(JsObject v) { return new Constant(v); }
	public static Constant valueOf(Word word) {
		switch (word.type()) {
			case JSLexer.NULL: return valueOf(JsNull.NULL);
			case JSLexer.UNDEFINED: return valueOf(JsNull.UNDEFINED);
			case Word.CHARACTER: case Word.STRING: return valueOf(new JsString(null, word.val()));
			case Word.LONG:
			case Word.DOUBLE: case Word.FLOAT: return valueOf(new JsDouble(null, word.asDouble()));
			case Word.INTEGER: return valueOf(new JsInt(null, word.asInt()));
			case JSLexer.TRUE: return valueOf(JsBool.TRUE);
			case JSLexer.FALSE: return valueOf(JsBool.FALSE);
			case JSLexer.NAN: return valueOf(JsDouble.NAN);
			case JSLexer.INFINITY: return valueOf(JsDouble.INF);
			default: throw OperationDone.NEVER;
		}
	}

	public Type type() { return c.type(); }
	public boolean isConstant() { return true; }
	public JsObject constVal() { return c; }

	@Override
	public void write(JsMethodWriter tree, boolean noRet) {
		if (noRet) throw new NotStatementException();

		switch (c.klassType()) {
			case INT:
				tree.ldc(c.asInt());
				tree.invokeS("roj/mildwind/JsContext", "getInt", "(I)Lroj/mildwind/type/JsInt;");
			break;
			case BOOL:
				tree.ldc(c.asBool());
				tree.invokeS("roj/mildwind/type/JsBool", "valueOf", "(I)Lroj/mildwind/type/JsObject;");
			break;
			case STRING:
				tree.ldc(c.toString());
				tree.invokeS("roj/mildwind/JsContext", "getStr", "(Ljava/lang/String;)Lroj/mildwind/type/JsString;");
			break;
			case DOUBLE:
				tree.ldc(c.asDouble());
				tree.invokeS("roj/mildwind/JsContext", "getInt", "(D)Lroj/mildwind/type/JsDouble;");
			break;
			case NAN:
				tree.field(Opcodes.GETSTATIC, "roj/mildwind/type/JsDouble", "NAN", "Lroj/mildwind/type/JsDouble;");
			break;
			case NULL:
				tree.field(Opcodes.GETSTATIC, "roj/mildwind/type/JsNull", "NULL", "Lroj/mildwind/type/JsNull;");
			break;
			case UNDEFINED:
				tree.field(Opcodes.GETSTATIC, "roj/mildwind/type/JsNull", "UNDEFINED", "Lroj/mildwind/type/JsNull;");
			break;
			default:
				tree.load(tree.sync(c));
			break;
		}
	}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof Constant)) return false;
		Constant cst = (Constant) left;
		return c.op_feq(cst.c);
	}

	@Override
	public JsObject compute(JsContext ctx) { return c; }

	@Override
	public String toString() { return c.toString(); }
}