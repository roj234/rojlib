package roj.lavac.expr;

import roj.asm.type.Type;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodPoetL;

import javax.annotation.Nonnull;

/**
 * @author Roj233
 * @since 2022/2/24 19:16
 */
public interface ASTNode {
	void write(MethodPoetL tree, boolean noRet) throws NotStatementException;

	@Nonnull
	default ASTNode compress() {
		return this;
	}

	Type type();

	default boolean isConstant() {
		return false;
	}

	default LDC asCst() {
		throw new IllegalArgumentException("This (" + this + ") - " + getClass().getName() + " is not a constant.");
	}

	boolean isEqual(ASTNode left);

	/**
	 * 特殊操作处理
	 *
	 * @param op_type 1: var_read; 2: var_write;
	 */
	default void mark_spec_op(MethodPoetL ctx, int op_type) {}

}
