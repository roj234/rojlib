package roj.compiler.plugins.eval;

import org.jetbrains.annotations.Nullable;
import roj.asm.tree.MethodNode;
import roj.asm.type.Type;
import roj.collect.SimpleList;
import roj.compiler.api.Evaluable;
import roj.compiler.ast.expr.Constant;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/5/30 0030 3:46
 */
final class CompiledMethod extends Evaluable {
	private final Evaluator evaluator;
	private final int methodId;
	private final Type returnType;

	CompiledMethod(Evaluator owner, int id, Type type) {
		evaluator = owner;
		methodId = id;
		returnType = type;
	}

	@Override
	public String toString() {return "Evaluable[Sandboxed] "+evaluator+"#"+methodId;}

	@Override
	public @Nullable ExprNode eval(MethodNode owner, @Nullable ExprNode self, List<ExprNode> args) {
		if (self != null) {
			args = new SimpleList<>(args);
			args.add(0, self);
		}

		Object[] val = new Object[args.size()];
		for (int i = 0; i < args.size(); i++) {
			ExprNode arg = args.get(i);
			if (!arg.isConstant() && !arg.isKind(ExprNode.ExprKind.LDC_CLASS)) return null;

			val[i] = arg.constVal();
		}

		try {
			Object result = evaluator.eval(methodId, val);
			return new Constant(returnType, result);
		} catch (Throwable e) {
			LocalContext.get().report(Kind.SEVERE_WARNING, "lava.sandbox.error", e.getMessage());
			return null;
		}
	}
}