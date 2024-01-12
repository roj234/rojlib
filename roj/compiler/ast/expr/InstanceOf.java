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
 * @since 2022/2/24 19:55
 */
final class InstanceOf implements ExprNode {
	private final Type type;
	private ExprNode left;

	public InstanceOf(Type type, ExprNode left) {
		this.type = type;
		this.left = left;
	}

	@Override
	public String toString() { return left+" instanceof "+type; }

	@Override
	public IType type() { return Type.std(Type.BOOLEAN); }

	@Override
	@SuppressWarnings("fallthrough")
	public ExprNode resolve(CompileContext ctx) {
		left = left.resolve(ctx);
		ctx.resolveType(type);
		if (left.type().isPrimitive()) throw new ResolveException("instanceOf.error.primitive");

		TypeCast.Cast cast = ctx.castTo(left.type(), type, TypeCast.E_NEVER);
		boolean result;
		switch (cast.type) {
			case TypeCast.E_NEVER: result = false; break;
			case TypeCast.E_NODATA: ctx.report(Diagnostic.Kind.ERROR,"symbol.error.noSuchClass:".concat(type.toString()));
			case TypeCast.E_DOWNCAST: return this;
			case TypeCast.UPCAST: result = true; break;
			default: throw new ResolveException("instanceOf.error.unknown_state:"+cast);
		}

		ctx.report(Diagnostic.Kind.MANDATORY_WARNING, "instanceOf.warn.always:".concat(type.toString()));
		return Constant.valueOf(result);
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) throws NotStatementException {
		left.write(cw, false);
		cw.clazz(Opcodes.INSTANCEOF, type);
	}

	@Override
	public boolean equalTo(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		InstanceOf of = (InstanceOf) o;

		if (!type.equals(of.type)) return false;
		return left.equalTo(of.left);
	}
}