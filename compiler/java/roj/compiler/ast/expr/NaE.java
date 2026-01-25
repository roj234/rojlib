package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.resolve.TypeCast;
import roj.compiler.types.VirtualType;

/**
 * Not an Expression
 * @author Roj234
 * @since 2024/5/30 1:40
 */
public final class NaE extends LeftValue {
	public static final Expr NOEXPR = new NaE();
	public static final IType UNRESOLVABLE = VirtualType.anyType("<unresolvable>");

	public static Expr resolveFailed() {return new NaE();}
	public static Expr resolveFailed(Expr expr) {
		NaE naE = new NaE();
		if (expr != null) {
			naE.wordStart = expr.wordStart;
			naE.wordEnd = expr.wordEnd;
		}
		return naE;
	}

	@Override public String toString() {return "<resolveFailed>";}
	@Override public IType type() {return UNRESOLVABLE;}

	@Override protected void preStore(MethodWriter cw) {}
	@Override protected void preLoadStore(MethodWriter cw) {}
	@Override protected void postStore(MethodWriter cw, int state) {}
	@Override protected int copyValue(MethodWriter cw, boolean twoStack) {return 0;}
	@Override protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) {}
}