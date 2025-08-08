package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.resolve.TypeCast;

/**
 * Not an Expression
 * @author Roj234
 * @since 2024/5/30 1:40
 */
public final class NaE extends Expr {
	public static final Expr NOEXPR = new NaE();
	public static final Generic UNRESOLVABLE = new Generic("<nae.unresolvable>");

	public static Expr resolveFailed() {return new NaE();}
	public static Expr resolveFailed(Expr expr) {
		NaE naE = new NaE();
		if (expr != null) {
			naE.wordStart = expr.wordStart;
			naE.wordEnd = expr.wordEnd;
		}
		return naE;
	}

	@Override public String toString() {return "<nae.unresolvable>";}
	@Override public IType type() {return UNRESOLVABLE;}
	@Override protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) {}
}