package roj.compiler.plugins.eval;

import org.jetbrains.annotations.Nullable;
import roj.asm.MethodNode;
import roj.asm.type.Type;
import roj.compiler.CompileContext;
import roj.compiler.api.InvokeHook;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.diagnostic.Kind;
import roj.concurrent.AsyncTask;
import roj.concurrent.TaskThread;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Roj234
 * @since 2024/5/30 3:46
 */
final class CompiledMethod extends InvokeHook {
	private final Evaluator evaluator;
	private final int methodId;
	private final Type returnType;
	static TaskThread runner;

	CompiledMethod(Evaluator owner, int id, Type type) {
		evaluator = owner;
		methodId = id;
		returnType = type;
	}

	@Override
	public String toString() {return "Evaluable "+evaluator+"#"+methodId;}

	@Override
	public @Nullable Expr eval(MethodNode owner, @Nullable Expr that, List<Expr> args, Invoke node) {
		for (int i = 0; i < args.size(); i++) {
			var arg = args.get(i);
			if (!arg.isConstant() && !arg.hasFeature(Expr.Feature.LDC_CLASS)) return null;
		}

		Object[] val;
		int i;

		if (that != null) {
			if (!that.isConstant() && !that.hasFeature(Expr.Feature.LDC_CLASS)) return null;

			val = new Object[args.size()+(i=1)];
			val[0] = that.constVal();
		} else {
			val = new Object[args.size()];
			i = 0;
		}

		for (int j = 0; j < args.size(); j++) val[i++] = args.get(j).constVal();

		AsyncTask<Object> task = new AsyncTask<>(() -> evaluator.eval(methodId, val));

		if (runner == null) {
			runner = new TaskThread();
			runner.setName("Sandbox Evaluator");
			runner.start();
		}
		runner.submit(task);

		try {
			try {
				Object result = task.get(1, TimeUnit.SECONDS);
				return Expr.constant(returnType, result);
			} catch (TimeoutException exc) {
				CompileContext.get().report(Kind.ERROR, "plugins.eval.constexpr.timeout", toString());
				// TODO better terminate, see Evaluator#forceStop
				while (runner.isAlive()) runner.stop();
				runner = null;
			} catch (ExecutionException e) {
				throw e.getCause();
			}
		} catch (Throwable e) {
			CompileContext.get().report(Kind.SEVERE_WARNING, "plugins.eval.constexpr.error", e.getCause());
		}

		return null;
	}
}