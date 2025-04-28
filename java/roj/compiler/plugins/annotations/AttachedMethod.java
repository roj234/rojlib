package roj.compiler.plugins.annotations;

import org.jetbrains.annotations.Nullable;
import roj.asm.MethodNode;
import roj.compiler.api.InvokeHook;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.Invoke;

import java.util.Arrays;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/6/10 0010 3:38
 */
final class AttachedMethod extends InvokeHook {
	private final MethodNode target;
	public AttachedMethod(MethodNode owner) {target = owner;}

	@Override
	public String toString() {return "attached<"+target+">";}

	@Override
	public Expr eval(MethodNode owner, @Nullable Expr self, List<Expr> args, Invoke node) {
		if (self != null) {
			var nodes = new Expr[args.size()+1];
			nodes[0] = self;
			for (int i = 0; i < args.size(); i++)
				nodes[i+1] = args.get(i);
			args = Arrays.asList(nodes);
		}
		return Invoke.staticMethod(target, args);
	}
}