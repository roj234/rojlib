package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.CompileContext;
import roj.compiler.context.CompileUnit;
import roj.compiler.resolve.ResolveException;
import roj.config.word.NotStatementException;

import javax.tools.Diagnostic;

/**
 * @author Roj234
 * @since 2023/1/30 0030 14:08
 */
final class This implements ExprNode {
	// TODO - concurrent (will be resolved via ReuseAST)
	@Deprecated
	public static final This THIS = new This();
	@Deprecated
	public static final This SUPER = new This();

	public This() {}

	private Type type;

	@Override
	public String toString() { return type != null ? type.toString() : this==THIS?"this":"super"; }

	@Override
	public ExprNode resolve(CompileContext ctx) throws ResolveException {
		if (type != null) return this;

		if (ctx.in_static) ctx.report(Diagnostic.Kind.ERROR, "this.error.static_context");

		CompileUnit file = ctx.file;
		if (file.parent == null) throw new ResolveException("this.error.no_super");

		type = new Type(this == THIS ? file.name : file.parent);
		return this;
	}

	@Override
	public IType type() { return type; }

	@Override
	public void write(MethodWriter cw, boolean noRet) throws NotStatementException {
		mustBeStatement(noRet);
		cw.one(Opcodes.ALOAD_0);
	}

	@Override
	public boolean equalTo(Object o) { return o == this; }
}