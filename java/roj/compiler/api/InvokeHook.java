package roj.compiler.api;

import org.jetbrains.annotations.Nullable;
import roj.asm.MethodNode;
import roj.asm.attr.Attribute;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.Invoke;

import java.util.List;

/**
 * 被解析阶段的{@link roj.compiler.ast.expr.Invoke}调用，可以修改或替换它
 * 该属性不会序列化
 * @author Roj234
 * @since 2024/4/15 10:35
 */
public abstract class InvokeHook extends Attribute {
	public static final String NAME = "LavaMethodHook";

	@Override public final boolean writeIgnore() {return true;}
	@Override public final String name() {return NAME;}

	@Nullable public abstract Expr eval(MethodNode owner, @Nullable Expr self, List<Expr> args, Invoke node);
}