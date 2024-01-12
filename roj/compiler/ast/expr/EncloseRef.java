package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.CompileContext;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.config.word.NotStatementException;

import javax.tools.Diagnostic;

/**
 * @author Roj234
 * @since 2023/1/30 0030 14:08
 */
final class EncloseRef implements ExprNode {
	public EncloseRef(boolean ThisEnclosing, Type type) {
		this.thisEnclosing = ThisEnclosing;
		this.type = type;
	}

	private IType type;
	final boolean thisEnclosing;

	@Override
	public String toString() { return type.toString() + (thisEnclosing?".this":".super"); }

	@Override
	public ExprNode resolve(CompileContext ctx) throws ResolveException {
		ctx.resolveType(type);
		if (thisEnclosing) {
			ctx.report(Diagnostic.Kind.ERROR, "this enclosing class暂未实现（non static ref）");
		} else {
			IType thisType = ctx.ep.This().resolve(ctx).type();
			TypeCast.Cast cast = ctx.castTo(thisType, type, TypeCast.E_NEVER);
			if (cast.type < 0) {
				ctx.report(Diagnostic.Kind.ERROR, "encloseRef.error.nds", thisType, type);
			} else if (cast.distance > 1) {
				ctx.report(Diagnostic.Kind.NOTE, "encloseRef.incompatible.nds", thisType, type);
			}
		}
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
	public boolean equalTo(Object o) {
		if (this == o) return true;
		if (!(o instanceof EncloseRef ref)) return false;
		return type.equals(ref.type);
	}
}