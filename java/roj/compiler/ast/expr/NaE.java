package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.resolve.TypeCast;

/**
 * Not an Expression
 * @author Roj234
 * @since 2024/5/30 0030 1:40
 */
public final class NaE extends ExprNode {
	public static final ExprNode NOEXPR = new NaE(), RESOLVE_FAILED = new NaE();
	public static final Generic UNRESOLVABLE = new Generic("<\1nae.unresolvable\0>");

	@Override public String toString() {return "<\1nae.unresolvable\0>";}
	@Override public IType type() {return UNRESOLVABLE;}
	@Override public void write(MethodWriter cw, boolean noRet) {}
	@Override public void write(MethodWriter cw, @Nullable TypeCast.Cast returnType) {}
}