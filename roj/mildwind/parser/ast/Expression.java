package roj.mildwind.parser.ast;

import roj.config.word.NotStatementException;
import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.parser.ParseContext;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.Type;

/**
 * @author Roj233
 * @since 2020/10/13 22:15
 */
public interface Expression {
	default Type type() { return Type.OBJECT; }

	void write(JsMethodWriter tree, boolean noRet) throws NotStatementException;

	default Expression compress() { return this; }

	default boolean isConstant() { return false; }
	default JsObject constVal() { throw new IllegalArgumentException(this + " (A " + getClass().getName() + ") is not constant."); }

	default boolean isEqual(Expression left) { return left == this; }

	default JsObject compute(JsObject ctx) { throw new UnsupportedOperationException(getClass().getName()); }

	/**
	 * 特殊操作处理
	 * @param op_type 1: var_read; 2: var_write;
	 */
	@Deprecated
	default void var_op(ParseContext ctx, int op_type) {}
}
