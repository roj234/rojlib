package roj.compiler.ast;

import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.context.LocalContext;

/**
 * Not an Expression
 * @author Roj234
 * @since 2024/5/30 0030 1:40
 */
public class NaE extends ExprNode {
	public static final NaE INSTANCE = new NaE();

	@Override
	public String toString() {return "<fallback>";}
	@Override
	public IType type() {return LocalContext.OBJECT_TYPE;}
	@Override
	public void write(MethodWriter cw, boolean noRet) {}
}