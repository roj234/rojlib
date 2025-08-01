package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.compiler.CompileContext;
import roj.compiler.asm.MethodWriter;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.TypeCast;

import java.util.List;

/**
 * @author Roj234
 * @since 2022/2/24 19:55
 */
public final class InstanceOf extends Expr {
	private IType type;
	private Expr left;
	private String variable;

	public InstanceOf(IType type, Expr left, String variable) {
		this.type = type;
		this.left = left;
		this.variable = variable;
	}

	public IType getVariableType() { return type; }
	public String getVariable() { return variable; }

	@Override
	public String toString() { return left+" instanceof "+type+(variable!=null?" "+variable:""); }

	@Override
	public IType type() { return Type.primitive(Type.BOOLEAN); }

	@Override
	@SuppressWarnings("fallthrough")
	public Expr resolve(CompileContext ctx) {
		left = left.resolve(ctx);

		IType lType = left.type();
		if (lType.isPrimitive()) {
			ctx.report(this, Kind.ERROR, "symbol.error.derefPrimitive");
			return NaE.resolveFailed(this);
		}

		if (ctx.resolveType(type).genericType() != IType.STANDARD_TYPE) {
			if (type.genericType() == IType.GENERIC_TYPE) {
				List<IType> children = ((Generic) type).children;
				for (int i = 0; i < children.size(); i++) {
					if (children.get(i) != Signature.any()) {
						ctx.report(this, Kind.ERROR, "instanceOf.error.unsafeCast", lType, type);
						break;
					}
				}
			} else {
				// typeParam
				ctx.report(this, Kind.ERROR, "instanceOf.error.unsafeCast", lType, type);
			}
		}

		var cast = ctx.castTo(lType, type, TypeCast.E_NEVER);
		boolean result;
		switch (cast.type) {
			default: ctx.report(this, Kind.ERROR, "typeCast.error."+cast.type, lType, type);
			case TypeCast.E_DOWNCAST: return this;
			case TypeCast.UPCAST: result = true; break;
			case TypeCast.E_NEVER: result = false; break;
		}

		ctx.report(this, Kind.SEVERE_WARNING, "instanceOf.constant", type);
		return valueOf(result);
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		left.write(cw);
		cw.clazz(Opcodes.INSTANCEOF, type.rawType());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof InstanceOf that)) return false;

		if (!type.equals(that.type)) return false;
		if (!left.equals(that.left)) return false;
		return variable != null ? variable.equals(that.variable) : that.variable == null;
	}

	@Override
	public int hashCode() {
		int result = type.hashCode();
		result = 31 * result + left.hashCode();
		result = 31 * result + (variable != null ? variable.hashCode() : 0);
		return result;
	}
}