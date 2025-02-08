package roj.compiler.plugins.moreop;

import org.jetbrains.annotations.NotNull;
import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.api.Types;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.VarNode;
import roj.compiler.context.LocalContext;
import roj.compiler.resolve.TypeCast;

/**
 * 操作符 - 获取列表某项
 * @author Roj234
 * @since 2024/12/1 8:24
 */
final class ListGet extends VarNode {
	private ExprNode list, index;
	private TypeCast.Cast cast;
	private IType componentType;

	ListGet(ExprNode list, ExprNode index) {
		this.list = list;
		this.index = index;
	}

	@Override public String toString() { return list.toString()+'['+index+']'; }
	@Override public IType type() { return componentType; }

	@NotNull
	@Override
	public ExprNode resolve(LocalContext ctx) {
		list = list.resolve(ctx);
		index = index.resolve(ctx);

		cast = ctx.castTo(index.type(), Type.std(Type.INT), 0);

		IType type = list.type();
		var types = ctx.inferGeneric(type, "java/util/List");
		componentType = types != null ? types.get(0) : Types.OBJECT_TYPE;
		return this;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);
		preStore(cw);
		cw.invokeItf("java/util/List", "get", "(I)Ljava/lang/Object;");
	}

	@Override
	public void preStore(MethodWriter cw) {
		//GenericSafe
		list.write(cw);
		index.write(cw, cast);
	}

	@Override
	public void preLoadStore(MethodWriter cw) {
		preStore(cw);
		cw.one(Opcodes.DUP2);
		cw.invokeItf("java/util/List", "get", "(I)Ljava/lang/Object;");
	}

	@Override
	public void postStore(MethodWriter cw, int state) {
		cw.invokeItf("java/util/List", "set", "(ILjava/lang/Object;)Ljava/lang/Object;");
		if (state == 0) cw.one(Opcodes.POP);
	}

	@Override
	public int copyValue(MethodWriter cw, boolean twoStack) {
		if (MoreOpPlugin.UseOriginalPut) return 1;
		cw.one(twoStack?Opcodes.DUP2_X2:Opcodes.DUP_X2);
		return 0;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ListGet)) return false;
		ListGet get = (ListGet) o;
		return get.list.equals(list) && get.index.equals(index);
	}

	@Override
	public int hashCode() {
		int result = list.hashCode();
		result = 31 * result + index.hashCode();
		return result;
	}
}