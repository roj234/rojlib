package roj.compiler.plugins.moreop;

import org.jetbrains.annotations.NotNull;
import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.compiler.CompileContext;
import roj.compiler.api.Types;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.LeftValue;
import roj.compiler.resolve.TypeCast;

/**
 * AST - 获取映射某项
 * @author Roj234
 * @since 2024/12/1 8:29
 */
final class MapGet extends LeftValue {
	private Expr map, index;
	private TypeCast.Cast cast;
	private IType componentType;

	MapGet(Expr map, Expr index) {
		this.map = map;
		this.index = index;
	}

	@Override public String toString() { return map.toString()+'['+index+']'; }
	@Override public IType type() { return componentType; }

	@NotNull
	@Override
	public Expr resolve(CompileContext ctx) {
		map = map.resolve(ctx);
		index = index.resolve(ctx);

		IType type = map.type();
		var types = ctx.inferGeneric(type, "java/util/Map");
		if (types != null) {
			componentType = types.get(0);
			cast = ctx.castTo(index.type(), types.get(1), 0);
		} else {
			componentType = Types.OBJECT_TYPE;
			cast = ctx.castTo(index.type(), Types.OBJECT_TYPE, 0);
		}
		return this;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);
		preStore(cw);
		cw.invokeItf("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
	}

	@Override
	public void preStore(MethodWriter cw) {
		//GenericSafe
		map.write(cw);
		index.write(cw, cast);
	}

	@Override
	public void preLoadStore(MethodWriter cw) {
		preStore(cw);
		cw.insn(Opcodes.DUP2);
		cw.invokeItf("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
	}

	@Override
	public void postStore(MethodWriter cw, int state) {
		cw.invokeItf("java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
		if (state == 0) cw.insn(Opcodes.POP);
	}

	@Override
	public int copyValue(MethodWriter cw, boolean twoStack) {
		if (MoreOpPlugin.UseOriginalPut) return 1;
		cw.insn(twoStack?Opcodes.DUP2_X2:Opcodes.DUP_X2);
		return 0;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof MapGet get)) return false;
		return get.map.equals(map) && get.index.equals(index);
	}

	@Override
	public int hashCode() {
		int result = map.hashCode();
		result = 31 * result + index.hashCode();
		return result;
	}
}