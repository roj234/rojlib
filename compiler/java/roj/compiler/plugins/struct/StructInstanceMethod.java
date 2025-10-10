package roj.compiler.plugins.struct;

import org.jetbrains.annotations.Nullable;
import roj.asm.MethodNode;
import roj.compiler.api.InvokeHook;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.Invoke;

import java.util.Arrays;
import java.util.List;

/**
 * @author Roj234
 * @since 2025/10/26 04:06
 */
final class StructInstanceMethod extends InvokeHook {
	@Override
	public Expr eval(MethodNode owner, @Nullable Expr that, List<Expr> args, Invoke node) {
		if (that != null) {
			var nodes = new Expr[args.size()+1];
			nodes[0] = that;
			for (int i = 0; i < args.size(); i++)
				nodes[i+1] = args.get(i);
			args = Arrays.asList(nodes);
		}
		return Invoke.staticMethod(owner, args);
	}

	@Override
	public String toString() {return "StructInstanceMethod";}
}
