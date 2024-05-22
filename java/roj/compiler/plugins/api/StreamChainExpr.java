package roj.compiler.plugins.api;

import org.jetbrains.annotations.Nullable;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.ast.expr.VarNode;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/2/20 0020 13:36
 */
public interface StreamChainExpr {
	List<Invoke> chain();

	/**
	 * StreamChain表达式源类型
	 * @return
	 * 0  从起始方法开始
	 * 1  从变量开始
	 */
	@Nullable
	VarNode sourceType();
	/**
	 * StreamChain表达式目标类型
	 * @return
	 * 0  立即终结
	 * 1  结果暂存到变量
	 * 2  结果被忽略
	 */
	int targetType();

	void fail(String reason);
	void fail(Throwable exception);

	MethodWriter writer();
}