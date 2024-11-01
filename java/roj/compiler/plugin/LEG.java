package roj.compiler.plugin;

import roj.compiler.JavaLexer;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.context.LocalContext;
import roj.config.ParseException;

/**
 * @author Roj234
 * @since 2024/8/31 0031 4:23
 */
public interface LEG {
	ExprNode generate(JavaLexer lexer, LocalContext ctx) throws ParseException;
}
