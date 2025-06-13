package roj.compiler.ast.expr;

import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.compiler.CompileContext;
import roj.compiler.api.Types;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.text.CharList;

import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/1/23 8:31
 */
final class MapLiteral extends Expr {
	private final List<Expr> keys, values;
	private Generic type;
	private boolean isDynamic;

	public <T> MapLiteral(List<Expr> keys, List<Expr> values) {
		this.keys = keys;
		this.values = values;
		assert keys.size() == values.size();
	}

	@Override
	public String toString() {
		var sb = new CharList().append('[');
		for (int i = 0; i < keys.size(); i++) {
			sb.append(keys.get(i)).append(" => ").append(values.get(i)).append(", ");
		}
		return sb.append(']').toStringAndFree();
	}

	@Override
	public Expr resolve(CompileContext ctx) throws ResolveException {
		if (type != null) return this;

		IType key = getCommonType(ctx, keys);
		IType val = getCommonType(ctx, values);

		type = new Generic("java/util/Map", 0, Generic.EX_NONE);

		if (key == null) {
			type.addChild(Asterisk.anyType);
			type.addChild(Asterisk.anyType);
		} else {
			var wrapper = TypeCast.getWrapper(key);
			type.addChild(wrapper == null ? key : wrapper);
			wrapper = TypeCast.getWrapper(val);
			type.addChild(wrapper == null ? val : wrapper);
		}
		return this;
	}

	private IType getCommonType(CompileContext ctx, List<Expr> nodes) {
		IType commonType = null;
		for (int i = 0; i < nodes.size(); i++) {
			var expr = nodes.get(i).resolve(ctx);
			nodes.set(i, expr);

			if (!expr.isConstant()) isDynamic = true;

			if (commonType == null) commonType = expr.type();
			else commonType = ctx.getCommonParent(commonType, expr.type());
		}
		return commonType;
	}

	@Override
	public IType type() { return type; }

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);
		cw.clazz(NEW, "roj/collect/MyHashMap");
		cw.insn(DUP);
		cw.ldc(keys.size());
		cw.invoke(INVOKESPECIAL, "roj/collect/MyHashMap", "<init>", "(I)V");
		var ctx = LocalContext.get();
		for (int i = 0; i < keys.size(); i++) {
			cw.insn(DUP);

			var key = keys.get(i);
			key.write(cw, ctx.castTo(key.type(), Types.OBJECT_TYPE, 0));
			var val = values.get(i);
			val.write(cw, ctx.castTo(val.type(), Types.OBJECT_TYPE, 0));

			cw.invoke(INVOKEVIRTUAL, "roj/collect/MyHashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
			cw.insn(POP);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		MapLiteral that = (MapLiteral) o;

		//if (isDynamic != that.isDynamic) return false;
		if (!keys.equals(that.keys)) return false;
		if (!values.equals(that.values)) return false;
		return type != null ? type.equals(that.type) : that.type == null;
	}

	@Override
	public int hashCode() {
		int result = keys.hashCode();
		result = 31 * result + values.hashCode();
		return result;
	}
}