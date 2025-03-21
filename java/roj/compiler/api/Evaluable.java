package roj.compiler.api;

import org.jetbrains.annotations.Nullable;
import roj.asm.MethodNode;
import roj.asm.attr.Attribute;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.Invoke;

import java.util.List;

/**
 * 被解析阶段的{@link roj.compiler.ast.expr.Invoke}调用，可以修改或替换它
 * 该属性不会序列化
 * @author Roj234
 * @since 2024/4/15 0015 10:35
 */
public abstract class Evaluable extends Attribute {
	public static final String NAME = "LavaEvaluable";

	@Override public final boolean writeIgnore() {return true;}
	@Override public final String name() {return NAME;}

	@Nullable public abstract ExprNode eval(MethodNode owner, @Nullable ExprNode self, List<ExprNode> args, Invoke node);
}