package roj.compiler.ast.expr;

/**
 * @author Roj234
 * @since 2024/2/20 15:05
 */
public abstract class PrefixOperator extends Expr {
	public abstract String setRight(Expr node);
}