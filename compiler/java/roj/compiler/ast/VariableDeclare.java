package roj.compiler.ast;

import org.jetbrains.annotations.NotNull;
import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.Expr;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;

/**
 * @author Roj234
 * @since 2024/2/21 23:18
 */
public final class VariableDeclare extends Expr {
	public final IType type;
	public final String name;
	public VariableDeclare(IType type, String name) {
		this.type = type;
		this.name = name;
	}

	public static VariableDeclare lambdaNamedOnly(String name) {
		return new VariableDeclare(null, name);
	}

	@Override public String toString() {return type == null ? name : type+" "+name;}
	@Override public IType type() {return type;}
	@Override protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) {throw ResolveException.ofInternalError("这不是表达式");}
}