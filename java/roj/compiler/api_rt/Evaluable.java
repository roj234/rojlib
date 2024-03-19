package roj.compiler.api_rt;

import org.jetbrains.annotations.Nullable;
import roj.asm.tree.attr.Attribute;
import roj.compiler.ast.expr.ExprNode;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/4/15 0015 10:35
 */
public abstract class Evaluable extends Attribute {
	@Override
	public final boolean isEmpty() {return true;}
	@Override
	public final String name() {return "Evaluable";}

	@Nullable
	public abstract ExprNode eval(@Nullable ExprNode self, List<ExprNode> args);
}