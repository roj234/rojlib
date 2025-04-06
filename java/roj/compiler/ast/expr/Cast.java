package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.compiler.Tokens;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.TypeCast;
import roj.config.data.CEntry;

/**
 * 强制类型转换
 *
 * @author Roj234
 * @since 2022/2/24 19:48
 */
final class Cast extends UnaryPre {
	TypeCast.Cast cast;

	public Cast(IType type) {
		super((short) 0);
		this.type = type;
	}

	@Override
	public String toString() { return "("+type+") "+right; }

	@NotNull
	@Override
	public ExprNode resolve(LocalContext ctx) {
		IType rType = (right = right.resolve(ctx)).type();
		ctx.resolveType(type);
		cast = ctx.castTo(rType, type, TypeCast.E_NEVER);
		castable:
		if (cast.type < TypeCast.E_DOWNCAST) {
			if (rType.isPrimitive()) {
				int wrapper = TypeCast.getWrappedPrimitive(type);
				if (wrapper != 0) {
					// allowing (Byte)3
					cast.type = TypeCast.BOXING;
					cast.box = (byte) wrapper;
					break castable;
				}
			}

			var override = ctx.getOperatorOverride(right, type, Tokens.lParen);
			if (override != null) return override;

			ctx.report(this, Kind.ERROR, "typeCast.error."+cast.type, rType, type);
			return NaE.RESOLVE_FAILED;
		}

		if (type.isPrimitive() && rType.isPrimitive() && right.isConstant()) {
			if (this.cast.isNoop()) {
				LocalContext.get().report(this, Kind.WARNING, "cast.warn.redundant", type);
			}
			return constant(type, AnnotationPrimer.castPrimitive((CEntry) right.constVal(), type));
		}
		return this;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);
		right.write(cw, cast);
	}

	@Override
	public void write(MethodWriter cw, @Nullable TypeCast.Cast returnType) {
		if (returnType != null && this.cast.isNoop()) {
			LocalContext.get().report(this, Kind.WARNING, "cast.warn.redundant", type);
		}

		right.write(cw, this.cast);
		if (returnType != null) returnType.write(cw);
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

	@Override
	public String setRight(ExprNode right) {
		if (right == null) return "unexpected_2:EOF:type.expr";
		this.right = right;
		return null;
	}
}