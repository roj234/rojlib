package roj.lavac.expr;

import roj.asm.type.IType;
import roj.config.word.NotStatementException;
import roj.lavac.parser.CompileUnit;
import roj.lavac.parser.MethodWriterL;

/**
 * @author Roj233
 * @since 2022/2/24 19:16
 */
public interface Expression {
	void write(MethodWriterL cw, boolean noRet) throws NotStatementException;

	default void preResolve(CompileUnit ctx, int flags) {}
	default Expression resolve() { return this; }

	IType type();

	default boolean isConstant() { return false; }
	default Object constVal() { throw new IllegalArgumentException("'"+this+"' ("+getClass().getName()+") is not a constant."); }

	boolean equals(Object left);

}
