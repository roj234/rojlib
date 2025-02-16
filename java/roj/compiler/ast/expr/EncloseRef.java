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
final class EncloseRef extends ExprNode {
	public EncloseRef(boolean ThisEnclosing, Type type) {
		this.thisEnclosing = ThisEnclosing;
		this.type = type;
	}

	private final IType type;
	final boolean thisEnclosing;
	private int thisEnclosingRef;

	@Override
	public String toString() { return type + (thisEnclosing?".this":".super"); }

	@Override
	public ExprNode resolve(LocalContext ctx) throws ResolveException {
		if (ctx.in_static) ctx.report(Kind.ERROR, "this.static");
		ctx.thisUsed = true;

		ctx.resolveType(type);
		if (thisEnclosing) {
			var fields = ctx.enclosing;
			check:
			if (fields.isEmpty()) {
				ctx.report(Kind.ERROR, "encloseRef.staticEnv");
			} else {
				for (int i = 0; i < fields.size();) {
					if (fields.get(i++).nestType().equals(type.owner())) {
						thisEnclosingRef = i;
						break check;
					}
				}
				ctx.report(Kind.ERROR, "encloseRef.noRef", type);
			}
		} else {
			var thisType = ctx.ep.This().resolve(ctx).type();
			var cast = ctx.castTo(thisType, type, TypeCast.E_NEVER);
			if (cast.type < 0) {
				ctx.report(Kind.ERROR, "encloseRef.nds", thisType, type);
			} else if (cast.distance > 1 || checkInterfaceInherit(ctx, type)) {
				ctx.report(Kind.INCOMPATIBLE, "encloseRef.tooFar", thisType, type);
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

	@Override
	public IType type() { return type; }

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		var lc = LocalContext.get();
		mustBeStatement(noRet);
		cw.vars(Opcodes.ALOAD, lc.thisSlot);

		if (thisEnclosingRef > 0) {
			var owner = lc.file.name();
			var fields = lc.enclosing;
			for (int i = fields.size()-1; i >= thisEnclosingRef; i--) {
				var fn = fields.get(i).nestRef();
				cw.field(Opcodes.GETFIELD, owner, fn.name(), fn.rawDesc());
				owner = fn.fieldType().owner();
			}
		}
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