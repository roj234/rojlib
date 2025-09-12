package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.compiler.CompileContext;
import roj.compiler.JavaTokenizer;
import roj.compiler.asm.MethodWriter;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.TypeCast;
import roj.config.node.ConfigValue;

/**
 * AST - 获取数组某项
 * * 允许重载
 * @author Roj233
 * @since 2022/2/27 20:09
 */
final class ArrayAccess extends LeftValue {
	private Expr array, index;
	private TypeCast.Cast cast;
	private IType componentType;

	ArrayAccess(Expr array, Expr index) {
		this.array = array;
		this.index = index;
	}

	@Override public String toString() { return array.toString()+'['+index+']'; }
	@Override public IType type() { return componentType; }

	@NotNull
	@Override
	public Expr resolve(CompileContext ctx) {
		array = array.resolve(ctx);
		index = index.resolve(ctx);

		IType type = array.type();
		if (type.array() == 0) {
			Expr override = ctx.getOperatorOverride(array, index, JavaTokenizer.lBracket);
			if (override != null) return override;

			ctx.report(this, Kind.ERROR, "arrayGet.notArray", type);
			return NaE.resolveFailed(this);
		}
		cast = ctx.castTo(index.type(), Type.primitive(Type.INT), 0);
		if (cast.type < 0) return NaE.resolveFailed(this);
		componentType = TypeHelper.componentType(type);

		if (array.isConstant()) {
			if (index.isConstant()) {
				ctx.report(this, Kind.WARNING, "arrayGet.constant");
				return constant(type(), ((Object[])array.constVal())[((ConfigValue) index.constVal()).asInt()]);
			}

			if (!ctx.inStatic) ctx.report(this, Kind.NOTE, "arrayGet.maybeStatic");
		}
		return this;
	}

	@Override
	protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) {
		mustBeStatement(cast);
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
		cw.insn(Opcodes.DUP2);
		cw.arrayLoad(type().rawType());
	}

	@Override
	public void postStore(MethodWriter cw, int state) { cw.arrayStore(type().rawType()); }

	@Override
	public int copyValue(MethodWriter cw, boolean twoStack) {cw.insn(twoStack?Opcodes.DUP2_X2:Opcodes.DUP_X2);return 0;}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ArrayAccess get)) return false;
		return get.array.equals(array) && get.index.equals(index);
	}

	@Override
	public int hashCode() {
		int result = array.hashCode();
		result = 31 * result + index.hashCode();
		return result;
	}
}