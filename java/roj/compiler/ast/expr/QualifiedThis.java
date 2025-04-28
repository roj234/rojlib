package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;

/**
 * @author Roj234
 * @since 2024/2/2 0002 6:15
 */
final class QualifiedThis extends Expr {
	// isThis true => XXX.this (has bytecode), false => XXX.super (only ALoad_0)
	public QualifiedThis(boolean isThis, Type type) {
		this.nestDepth = isThis ? -1 : 0;
		this.type = type;
	}

	private final IType type;
	int nestDepth;

	@Override
	public String toString() { return type + (nestDepth!=0?".this":".super"); }

	@Override
	public Expr resolve(LocalContext ctx) throws ResolveException {
		if (nestDepth > 0) return this;

		if (ctx.inStatic) ctx.report(this, Kind.ERROR, "this.static");
		ctx.thisUsed = true;

		ctx.resolveType(type);
		if (nestDepth < 0) {
			var enclosing = ctx.enclosing;
			for (int i = 0; i < enclosing.size();) {
				if (enclosing.get(i++).nestHost().equals(type.owner())) {
					nestDepth = i;
					return this;
				}
			}

			nestDepth = 0;
			if (!type.owner().equals(ctx.file.name())) {
				ctx.report(this, Kind.ERROR, "encloseRef.noRef", type);
			}
		} else {
			var thisType = ctx.ep.This().resolve(ctx).type();
			var cast = ctx.castTo(thisType, type, TypeCast.E_NEVER);
			if (cast.type < 0) {
				ctx.report(this, Kind.ERROR, "encloseRef.nds", thisType, type);
			} else if (cast.distance > 1 || checkInterfaceInherit(ctx, type)) {
				ctx.report(this, Kind.INCOMPATIBLE, "encloseRef.tooFar", thisType, type);
			}
		}
		return this;
	}

	private static boolean checkInterfaceInherit(LocalContext ctx, IType type) {
		String target = type.owner();
		for (String itf : ctx.file.interfaces()) {
			if (itf.equals(target)) continue;
			if (ctx.instanceOf(itf, target))
				return true;
		}
		return false;
	}

	@Override public IType type() { return type; }

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		var ctx = LocalContext.get();
		mustBeStatement(noRet);
		cw.vars(Opcodes.ALOAD, ctx.thisSlot);

		if (nestDepth > 0) {
			var owner = ctx.file.name();
			var fields = ctx.enclosing;
			for (int i = fields.size()-1; i >= nestDepth-1; i--) {
				var fn = fields.get(i).nestHostRef();
				if (fn != null) {
					cw.field(Opcodes.GETFIELD, owner, fn.name(), fn.rawDesc());
					owner = fn.fieldType().owner();
				}
			}
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof QualifiedThis ref)) return false;
		return type.equals(ref.type);
	}

	@Override
	public int hashCode() { return type.hashCode(); }
}