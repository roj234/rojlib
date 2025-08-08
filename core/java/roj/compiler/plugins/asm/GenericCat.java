package roj.compiler.plugins.asm;

import org.jetbrains.annotations.NotNull;
import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.Expr;
import roj.compiler.resolve.TypeCast;
import roj.util.OperationDone;

/**
 * @author Roj234
 * @since 2024/6/4 22:52
 */
final class GenericCat extends Expr {
	private final Expr node;
	public GenericCat(Expr node) {this.node = node;}

	@Override
	public String toString() {return "genericCast("+node+")";}
	@Override
	public IType type() {return node.type().rawType();}

	@Override
	protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) {throw OperationDone.NEVER;}
	@Override
	public void write(MethodWriter cw, @NotNull TypeCast.Cast cast) {node.write(cw, cast);}
}