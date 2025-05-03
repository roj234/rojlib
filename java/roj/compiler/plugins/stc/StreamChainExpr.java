package roj.compiler.plugins.stc;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.ast.expr.LeftValue;
import roj.compiler.context.LocalContext;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/2/20 13:36
 */
public interface StreamChainExpr {
	@Contract(pure = true)
	List<Invoke> chain();

	/**
	 * StreamChain表达式源类型
	 * @return
	 * null  起始方法
	 * !null 之前暂存的变量(WIP)
	 */
	@Contract(pure = true)
	@Nullable LeftValue sourceType();
	/**
	 * StreamChain表达式目标类型
	 * @return
	 * 0  终结方法
	 * 1  暂存到变量(WIP)
	 * 2  忽略
	 */
	@Contract(pure = true)
	int targetType();
	int TERM = 0, TEMP = 1, IGNORE = 2;

	@Contract(pure = true)
	LocalContext context();
	@Contract(pure = true)
	MethodWriter writer();
}