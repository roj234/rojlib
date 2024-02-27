package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;

import java.util.List;

/**
 * @author Roj234
 * @since 2022/2/24 19:55
 */
public final class InstanceOf extends ExprNode {
	private IType type;
	private ExprNode left;
	private String variable;

	public InstanceOf(IType type, ExprNode left, String variable) {
		this.type = type;
		this.left = left;
		this.variable = variable;
	}

	public IType getVariableType() { return type; }
	public String getVariable() { return variable; }

	@Override
	public String toString() { return left+" instanceof "+type+(variable!=null?" "+variable:""); }

	@Override
	public IType type() { return Type.std(Type.BOOLEAN); }

	@Override
	@SuppressWarnings("fallthrough")
	public ExprNode resolve(LocalContext ctx) {
		left = left.resolve(ctx);
		ctx.resolveType(type);

		if (left.type().isPrimitive()) ctx.report(Kind.ERROR, "symbol.error.derefPrimitive");

		if (type.genericType() != IType.STANDARD_TYPE) {
			if (type.genericType() == IType.GENERIC_TYPE) {
				List<IType> children = ((Generic) type).children;
				for (int i = 0; i < children.size(); i++) {
					if (children.get(i) != Signature.any()) {
						ctx.report(Kind.ERROR, "instanceOf.error.unsafeCast", left.type(), type);
						break;
					}
				}
			} else {
				// typeParam
				ctx.report(Kind.ERROR, "instanceOf.error.unsafeCast", left.type(), type);
			}
		}

		TypeCast.Cast cast = ctx.castTo(left.type(), type, TypeCast.E_NEVER);
		boolean result;
		switch (cast.type) {
			case TypeCast.E_NEVER: result = false; break;
			case TypeCast.E_NODATA: ctx.report(Kind.ERROR, "symbol.error.noSuchClass:", type);
			case TypeCast.E_DOWNCAST: return this;
			case TypeCast.UPCAST: result = true; break;
			default: throw new ResolveException("unknownState-"+cast);
		}

		ctx.report(Kind.SEVERE_WARNING, "instanceOf.constant", type.toString());
		return Constant.valueOf(result);
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		left.write(cw, false);
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