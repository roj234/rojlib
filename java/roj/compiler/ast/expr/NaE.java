package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
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
	public static final Expr NOEXPR = new NaE(), RESOLVE_FAILED = new NaE();
	public static final Generic UNRESOLVABLE = new Generic("<nae.unresolvable>");

	@Override public String toString() {return "<nae.unresolvable>";}
	@Override public IType type() {return UNRESOLVABLE;}
	@Override public void write(MethodWriter cw, boolean noRet) {}
	@Override public void write(MethodWriter cw, @Nullable TypeCast.Cast returnType) {}
}