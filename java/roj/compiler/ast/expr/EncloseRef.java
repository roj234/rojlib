package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.CompileContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;

/**
 * @author Roj234
 * @since 2024/2/2 0002 6:15
 */
final class EncloseRef extends ExprNode {
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
			ctx.report(Kind.ERROR, "this enclosing class暂未实现（non static ref）");
		} else {
			IType thisType = ctx.ep.This().resolve(ctx).type();
			TypeCast.Cast cast = ctx.castTo(thisType, type, TypeCast.E_NEVER);
			if (cast.type < 0) {
				ctx.report(Kind.ERROR, "encloseRef.error.nds", thisType, type);
			} else if (cast.distance > 1) {
				ctx.report(Kind.NOTE, "encloseRef.incompatible.nds", thisType, type);
			}
		}
		return this;
	}

	@Override
	public IType type() { return type; }

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);
		cw.one(Opcodes.ALOAD_0);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof EncloseRef ref)) return false;
		return type.equals(ref.type);
	}

	@Override
	public int hashCode() { return type.hashCode(); }
}