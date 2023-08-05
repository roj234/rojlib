package roj.lavac.expr;

import roj.compiler.ast.expr.ExprNode;
import roj.lavac.parser.MethodWriterL;

/**
 * @author Roj234
 * @since 2020/11/1 14:14
 */
public interface LoadNode extends ExprNode {
	void writeLoad(MethodWriterL tree);
}
