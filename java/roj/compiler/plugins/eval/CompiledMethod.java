package roj.compiler.plugins.eval;

import org.jetbrains.annotations.Nullable;
import roj.asm.MethodNode;
import roj.asm.type.Type;
import roj.compiler.api.Evaluable;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.Invoke;
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
	public @Nullable ExprNode eval(MethodNode owner, @Nullable ExprNode self, List<ExprNode> args, Invoke node) {
		for (int i = 0; i < args.size(); i++) {
			var arg = args.get(i);
			if (!arg.isConstant() && !arg.hasFeature(ExprNode.ExprFeat.LDC_CLASS)) return null;
		}

		Object[] val;
		int i;

		if (self != null) {
			if (!self.isConstant() && !self.hasFeature(ExprNode.ExprFeat.LDC_CLASS)) return null;

			val = new Object[args.size()+(i=1)];
			val[0] = self.constVal();
		} else {
			val = new Object[args.size()];
			i = 0;
		}

		for (int j = 0; j < args.size(); j++) val[i++] = args.get(j).constVal();

		try {
			var result = evaluator.eval(methodId, val);
			return ExprNode.constant(returnType, result);
		} catch (Throwable e) {
			LocalContext.get().report(Kind.SEVERE_WARNING, "lava.sandbox.error", e.getMessage());
			return null;
		}
	}
}