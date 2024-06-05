package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;

/**
 * @author Roj234
 * @since 2023/1/30 0030 14:08
 */
final class This extends ExprNode {
	private final boolean isThis;
	This(boolean isThis) {this.isThis = isThis;}

	private Type type;

	@Override
	public String toString() { return (isThis?"<this>(":"<super>(")+type+")"; }

	@Override
	public ExprNode resolve(LocalContext ctx) throws ResolveException {
		if (type != null) return this;

		if (ctx.in_static) ctx.report(Kind.ERROR, "this.static");

		CompileUnit file = ctx.file;
		if (file.parent == null) throw new ResolveException("this.no_super:"+file.name);

		type = new Type(isThis ? file.name : file.parent);
		return this;
	}

	@Override
	public IType type() { return type; }

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);
		cw.one(Opcodes.ALOAD_0);
	}
}