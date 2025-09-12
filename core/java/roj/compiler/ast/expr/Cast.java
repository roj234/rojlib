package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.type.IType;
import roj.compiler.CompileContext;
import roj.compiler.JavaTokenizer;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.asm.MethodWriter;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.TypeCast;
import roj.config.data.CEntry;

/**
 * AST - 强制类型转换
 * @author Roj234
 * @since 2022/2/24 19:48
 */
final class Cast extends PrefixOp {
	TypeCast.Cast cast;

	public Cast(IType type) {
		super((short) 0);
		this.type = type;
	}

	@Override
	public String toString() { return "("+type+") "+right; }

	@NotNull
	@Override
	public Expr resolve(CompileContext ctx) {
		IType rType = (right = right.resolve(ctx)).type();
		type = ctx.resolveType(type);
		cast = ctx.castTo(rType, type, TypeCast.IMPOSSIBLE);
		castable:
		if (cast.type < TypeCast.DOWNCAST) {
			if (rType.isPrimitive()) {
				int wrapper = TypeCast.getWrappedPrimitive(type);
				if (wrapper != 0) {
					// allowing (Byte)3
					cast.type = TypeCast.BOXING;
					cast.box = (byte) wrapper;
					break castable;
				}
			}

			var override = ctx.getOperatorOverride(right, type, JavaTokenizer.lParen);
			if (override != null) return override;

			ctx.report(this, Kind.ERROR, "typeCast.error."+cast.type, rType, type);
			return NaE.resolveFailed(this);
		}

		if (type.isPrimitive() && rType.isPrimitive() && right.isConstant()) {
			if (this.cast.isIdentity()) {
				CompileContext.get().report(this, Kind.WARNING, "cast.redundant", type);
			}
			return constant(type, AnnotationPrimer.castPrimitive((CEntry) right.constVal(), type));
		}
		return this;
	}

	@Override
	protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) {
		if (cast.isIdentity() && this.cast.isIdentity()) {
			CompileContext.get().report(this, Kind.WARNING, "cast.redundant", type);
		}

		right.write(cw, this.cast);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Cast cast = (Cast) o;

		if (!type.equals(cast.type)) return false;
		return right.equals(cast.right);
	}
	@Override
	public int hashCode() {return 31 * super.hashCode() + type.hashCode();}
}