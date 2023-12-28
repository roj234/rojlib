package roj.mildwind.parser.ast;

import roj.config.word.NotStatementException;
import roj.mildwind.JsContext;
import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.type.JsObject;

/**
 * @author Roj234
 * @since 2020/11/1 14:14
 */
interface LoadExpression extends Expression {
	@Override
	default void write(JsMethodWriter tree, boolean noRet) throws NotStatementException {
		writeLoad(tree);
		writeExecute(tree, noRet);
	}

	void writeLoad(JsMethodWriter tree);
	void writeExecute(JsMethodWriter tree, boolean noRet) throws NotStatementException;

	void computeAssign(JsContext ctx, JsObject val);

	boolean setDeletion();
}