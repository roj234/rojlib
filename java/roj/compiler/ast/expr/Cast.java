package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.compiler.JavaLexer;
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
		if (cast.type < TypeCast.E_DOWNCAST) {
			var override = ctx.getOperatorOverride(right, type, JavaLexer.lParen);
			if (override != null) return override;

			ctx.report(Kind.ERROR, "typeCast.error."+cast.type, rType, type);
			//return NaE.RESOLVE_FAILED;
		}

		if (type.isPrimitive() && rType.isPrimitive() && right.isConstant()) {
			return new Constant(type, AnnotationPrimer.castPrimitive((CEntry) right.constVal(), type));
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
		if (returnType != null && this.cast.type >= 0 && this.cast.getOp1() != 42/*Do not check for AnyCast*/) {
			LocalContext.get().report(Kind.WARNING, "cast.warn.redundant", type);
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