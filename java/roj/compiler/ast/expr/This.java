package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.CompileContext;
import roj.compiler.asm.MethodWriter;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;

/**
 * @author Roj234
 * @since 2023/1/30 14:08
 */
final class This extends Expr {
	private final boolean isThis;
	This(boolean isThis) {super(0);this.isThis = isThis;}

	private final Type type = Type.klass("");

	@Override
	public String toString() { return (isThis?"this<":"super<")+type+'>'; }

	@Override
	public Expr resolve(CompileContext ctx) throws ResolveException {
		var file = ctx.file;
		type.owner = isThis ? file.name() : file.parent();
		if (type.owner == null) throw ResolveException.ofIllegalInput("this.no_super", file);
		return this;
	}

	@Override
	public IType type() { return type; }

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);

		var ctx = CompileContext.get();
		// 不在上面检查是为了Invoke local static method
		if (ctx.inStatic) ctx.report(this, Kind.ERROR, "this.static");

		ctx.thisUsed = true;
		cw.vars(Opcodes.ALOAD, ctx.thisSlot);
	}
}