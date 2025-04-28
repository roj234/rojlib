package roj.compiler.ast.expr;

import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;

/**
 * @author Roj234
 * @since 2020/11/1 14:14
 */
public abstract class LeftValue extends Expr {
	protected LeftValue() {}
	protected LeftValue(int _noUpdate) {super(0);}

	@Override
	public LeftValue asLeftValue(LocalContext ctx) {
		if (isFinal()) {
			ctx.report(this, Kind.ERROR, "var.assignFinal", this);
			return null;
		}
		return this;
	}

	boolean isFinal() { return false; }
	protected abstract void preStore(MethodWriter cw);
	protected abstract void preLoadStore(MethodWriter cw);
	protected abstract void postStore(MethodWriter cw, int state);
	protected abstract int copyValue(MethodWriter cw, boolean twoStack);
	boolean hasSideEffect() { return true; }
}