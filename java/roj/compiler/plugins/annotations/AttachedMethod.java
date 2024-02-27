package roj.compiler.plugins.annotations;

import org.jetbrains.annotations.Nullable;
import roj.asm.tree.MethodNode;
import roj.compiler.api.Evaluable;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.Invoke;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/6/10 0010 3:38
 */
final class AttachedMethod extends Evaluable {
	private final MethodNode target;
	public AttachedMethod(MethodNode owner) {target = owner;}

	@Override
	public String toString() {return "attached<"+target+">";}

	@Override
	public ExprNode eval(MethodNode owner, @Nullable ExprNode self, List<ExprNode> args) {
		ExprNode[] nodes;
		if (self == null) {
			nodes = args.toArray(new ExprNode[args.size()]);
		} else {
			nodes = new ExprNode[args.size()+1];
			nodes[0] = self;
			for (int i = 0; i < args.size(); i++)
				nodes[i+1] = args.get(i);
		}
		return Invoke.staticMethod(target, nodes);
	}
}