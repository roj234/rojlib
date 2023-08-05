package roj.compiler.ast.expr;

import roj.asm.type.IType;
import roj.config.word.NotStatementException;
import roj.lavac.block.Node;
import roj.lavac.parser.CompileUnit;
import roj.lavac.parser.MethodWriterL;

/**
 * @author Roj233
 * @since 2022/2/24 19:16
 */
public interface ExprNode extends Node {
	void write(MethodWriterL cw, boolean noRet) throws NotStatementException;

	default void preResolve(CompileUnit ctx, int flags) {}
	default ExprNode resolve() { return this; }

	IType type();

	default boolean isConstant() { return false; }
	default Object constVal() { throw new IllegalArgumentException("'"+this+"' ("+getClass().getName()+") is not a constant."); }

	// FIXME renamed, should review code
	boolean equalTo(Object left);
}
