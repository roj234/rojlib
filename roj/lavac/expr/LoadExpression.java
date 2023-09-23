package roj.lavac.expr;

import roj.lavac.parser.MethodWriterL;

/**
 * @author Roj234
 * @since 2020/11/1 14:14
 */
public interface LoadExpression extends Expression {
	void writeLoad(MethodWriterL tree);
}
