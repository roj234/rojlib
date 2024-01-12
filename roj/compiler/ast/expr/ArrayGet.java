package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.Opcodes;
import roj.asm.tree.anno.AnnVal;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.InsnHelper;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.CompileContext;
import roj.compiler.resolve.TypeCast;

import javax.tools.Diagnostic;

/**
 * 操作符 - 获取数组某项
 *
 * @author Roj233
 * @since 2022/2/27 20:09
 */
final class ArrayGet implements VarNode {
	private ExprNode array, index;
	private TypeCast.Cast cast;

	ArrayGet(ExprNode array, ExprNode index) {
		this.array = array;
		this.index = index;
	}

	@Override
	public String toString() { return array.toString()+'['+index+']'; }

	@Override
	public IType type() { return TypeHelper.componentType(array.type()); }

	@NotNull
	@Override
	public ExprNode resolve(CompileContext ctx) {
		array = array.resolve(ctx);
		index = index.resolve(ctx);

		if (array.type().array() == 0) ctx.report(Diagnostic.Kind.ERROR, "arrayGet.error.notArray:"+array.type());
		cast = ctx.castTo(index.type(), Type.std(Type.INT), 0);

		if (array.isConstant()) {
			if (index.isConstant()) {
				ctx.report(Diagnostic.Kind.WARNING, "arrayGet.warn.constant");
				return new Constant(type(), ((Object[])array.constVal())[((AnnVal) index.constVal()).asInt()]);
			}

			if (!ctx.in_static) ctx.report(Diagnostic.Kind.NOTE, "arrayGet.note.uselessCreation");
		}
		return this;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);
		preStore(cw);
		cw.one(InsnHelper.XALoad(type().rawType()));
	}

	@Override
	public void preStore(MethodWriter cw) {
		//GenericSafe
		array.write(cw, false);
		index.writeDyn(cw, cast);
	}

	@Override
	public void preLoadStore(MethodWriter cw) {
		preStore(cw);
		cw.one(Opcodes.DUP2);
		cw.one(InsnHelper.XALoad(type().rawType()));
	}

	@Override
	public void postStore(MethodWriter cw) { cw.one(InsnHelper.XAStore(type().rawType())); }

	@Override
	public void copyValue(MethodWriter cw, boolean twoStack) { cw.one(twoStack?Opcodes.DUP2_X2:Opcodes.DUP_X2); }

	@Override
	public boolean equalTo(Object o) {
		if (this == o) return true;
		if (!(o instanceof ArrayGet)) return false;
		ArrayGet get = (ArrayGet) o;
		return get.array.equalTo(array) && get.index.equalTo(index);
	}
}