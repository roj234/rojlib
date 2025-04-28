package roj.compiler.ast;

import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.Expr;
import roj.compiler.resolve.ResolveException;

/**
 * @author Roj234
 * @since 2024/2/21 0021 23:18
 */
public final class VariableDeclare extends Expr {
	public final IType type;
	public final String name;
	public VariableDeclare(IType type, String name) {
		this.type = type;
		this.name = name;
	}

	@Override public String toString() {return type+" "+name;}
	@Override public IType type() {return type;}
	@Override public void write(MethodWriter cw, boolean noRet) {throw ResolveException.ofInternalError("这不是表达式");}
}