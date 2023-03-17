package roj.kscript.parser.ast;

import roj.kscript.asm.KS_ASM;
import roj.kscript.type.KType;

import java.util.Map;

/**
 * @author Roj234
 * @since 2020/11/1 14:14
 */
public interface LoadExpression extends Expression {
	void writeLoad(KS_ASM tree);

	void assignInCompute(Map<String, KType> param, KType val);

	boolean setDeletion();
}
