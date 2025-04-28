package roj.compiler.plugins.asm;

import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.Expr;

/**
 * @author Roj234
 * @since 2024/6/4 0004 22:52
 */
final class GenericCat extends Expr {
	private final Expr node;
	public GenericCat(Expr node) {this.node = node;}

	@Override
	public String toString() {return "genericCast("+node+")";}
	@Override
	public IType type() {return node.type().rawType();}
	@Override
	public void write(MethodWriter cw, boolean noRet) {node.write(cw, noRet);}
}