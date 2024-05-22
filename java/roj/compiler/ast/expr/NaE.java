package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.asm.MethodWriter;
import roj.compiler.resolve.TypeCast;

/**
 * Not an Expression
 * @author Roj234
 * @since 2024/5/30 0030 1:40
 */
public final class NaE extends ExprNode {
	public static final ExprNode NOEXPR = new NaE(), RESOLVE_FAILED = new NaE();

	@Override
	public String toString() {return "<fallback>";}
	@Override
	public IType type() {return Type.std(Type.VOID);}
	@Override
	public void write(MethodWriter cw, boolean noRet) {}
	@Override
	public void writeDyn(MethodWriter cw, @Nullable TypeCast.Cast cast) {}
}