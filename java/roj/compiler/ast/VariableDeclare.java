package roj.compiler.ast;

import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.context.LocalContext;
import roj.compiler.resolve.ResolveException;

/**
 * @author Roj234
 * @since 2024/2/21 0021 23:18
 */
public final class VariableDeclare extends ExprNode {
	public final IType type;
	public final String name;
	public VariableDeclare(IType type, String name) {
		this.type = type;
		this.name = name;
	}

	@Override
	public String toString() {return type+" "+name;}
	@Override
	public IType type() { return type; }
	@Override
	public ExprNode resolve(LocalContext ctx) throws ResolveException { throw new ResolveException("not expression"); }
	@Override
	public void write(MethodWriter cw, boolean noRet) {throw new ResolveException("not expression");}
}