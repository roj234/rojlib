package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.CompileContext;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.config.word.NotStatementException;

/**
 * todo 放一个 int pos 来方便report
 * @author Roj233
 * @since 2022/2/24 19:16
 */
public interface ExprNode {
	String toString();

	IType type();
	default ExprNode resolve(CompileContext ctx) throws ResolveException { return this; }

	default boolean isConstant() { return false; }
	default Object constVal() { throw new IllegalArgumentException("'"+this+"' ("+getClass().getName()+") is not a constant."); }

	void write(MethodWriter cw, boolean noRet) throws NotStatementException;
	default void writeDyn(MethodWriter cw, @Nullable TypeCast.Cast cast) {
		write(cw, false);
		if (cast != null) cast.write(cw);
	}
	// use lavac future
	default void mustBeStatement(boolean noRet) { if (noRet) throw new NotStatementException(); }

	boolean equalTo(Object o);
}