package roj.lavac.expr;

import roj.lavac.parser.MethodPoetL;

/**
 * @author Roj234
 * @since 2020/11/1 14:14
 */
public interface LoadExpression extends Expression {
	int DYNAMIC_FIELD = 0, STATIC_FIELD = 1, VARIABLE = 2, ARRAY = 3;

	void write2(MethodPoetL tree);

	byte loadType();

	default void resolve() {}
}
