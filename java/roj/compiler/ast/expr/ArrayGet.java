package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.compiler.Tokens;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.TypeCast;
import roj.config.data.CEntry;

/**
 * 操作符 - 获取数组某项
 *
 * @author Roj233
 * @since 2022/2/27 20:09
 */
final class ArrayGet extends VarNode {
	private ExprNode array, index;
	private TypeCast.Cast cast;
	private IType componentType;

	ArrayGet(ExprNode array, ExprNode index) {
		this.array = array;
		this.index = index;
	}

	@Override public String toString() { return array.toString()+'['+index+']'; }
	@Override public IType type() { return componentType; }

	@NotNull
	@Override
	public ExprNode resolve(LocalContext ctx) {
		array = array.resolve(ctx);
		index = index.resolve(ctx);

		IType type = array.type();
		if (type.array() == 0) {
			ExprNode override = ctx.getOperatorOverride(array, index, Tokens.lBracket);
			if (override != null) return override;

			ctx.report(this, Kind.ERROR, "arrayGet.error.notArray", type);
			return NaE.RESOLVE_FAILED;
		}
		cast = ctx.castTo(index.type(), Type.primitive(Type.INT), 0);
		if (cast.type < 0) return NaE.RESOLVE_FAILED;
		componentType = TypeHelper.componentType(type);

		if (array.isConstant()) {
			if (index.isConstant()) {
				ctx.report(this, Kind.WARNING, "arrayGet.warn.constant");
				return constant(type(), ((Object[])array.constVal())[((CEntry) index.constVal()).asInt()]);
			}

			if (!ctx.inStatic) ctx.report(this, Kind.NOTE, "arrayGet.note.uselessCreation");
		}
		return this;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);
		preStore(cw);
		cw.arrayLoad(type().rawType());
	}

	@Override
	public void preStore(MethodWriter cw) {
		//GenericSafe
		array.write(cw);
		index.write(cw, cast);
	}

	@Override
	public void preLoadStore(MethodWriter cw) {
		preStore(cw);
		cw.one(Opcodes.DUP2);
		cw.arrayLoad(type().rawType());
	}

	@Override
	public void postStore(MethodWriter cw, int state) { cw.arrayStore(type().rawType()); }

	@Override
	public int copyValue(MethodWriter cw, boolean twoStack) {cw.one(twoStack?Opcodes.DUP2_X2:Opcodes.DUP_X2);return 0;}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ArrayGet)) return false;
		ArrayGet get = (ArrayGet) o;
		return get.array.equals(array) && get.index.equals(index);
	}

	@Override
	public int hashCode() {
		int result = array.hashCode();
		result = 31 * result + index.hashCode();
		return result;
	}
}