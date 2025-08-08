package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.CompileContext;
import roj.compiler.asm.MethodWriter;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.NestContext;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;

/**
 * @author Roj234
 * @since 2024/2/2 6:15
 */
final class QualifiedThis extends Expr {
	// isThis true => XXX.this (has bytecode), false => XXX.super (only ALoad_0)
	public QualifiedThis(boolean isThis, Type type) {
		this.nestDepth = isThis ? -1 : 0;
		this.type = type;
	}

	private IType type;
	int nestDepth;

	@Override
	public String toString() { return type + (nestDepth!=0?".this":".super"); }

	@Override
	public Expr resolve(CompileContext ctx) throws ResolveException {
		if (nestDepth > 0) return this;

		if (ctx.inStatic) ctx.report(this, Kind.ERROR, "this.static");
		ctx.thisUsed = true;

		type = ctx.resolveType(type);
		if (nestDepth < 0) {
			var enclosing = ctx.enclosingContext();
			for (int i = 0; i < enclosing.size();) {
				NestContext nestContext = enclosing.get(i++);
				if (nestContext.nestHost().equals(type.owner())) {
					if (nestContext.inStatic()) ctx.report(this, Kind.ERROR, "this.static.other", nestContext.nestHost());
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
			var cast = ctx.caster.checkCast(thisType, type);
			if (cast.type < 0) {
				ctx.report(this, Kind.ERROR, "encloseRef.nds", thisType, type);
			} else if (cast.distance > 1 || checkInterfaceInherit(ctx, type)) {
				ctx.report(this, Kind.INCOMPATIBLE, "encloseRef.tooFar", thisType, type);
			}
		}
		return this;
	}

	private static boolean checkInterfaceInherit(CompileContext ctx, IType type) {
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
	protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) {
		var ctx = CompileContext.get();
		mustBeStatement(cast);

		if (nestDepth > 0) {
			var fields = ctx.enclosingContext();
			var result = fields.get(nestDepth-1).nestHostRef();
			for (int i = nestDepth-1; i < fields.size(); i++) {
				result = fields.get(i).transferInto(ctx, result, NestContext.InnerClass.FIELD_HOST_REF);
			}
			result.parent().write(cw, noRet(cast));
		} else {
			cw.vars(Opcodes.ALOAD, ctx.thisSlot);
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