package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.util.InsnHelper;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.CompileContext;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.text.CharList;

import javax.tools.Diagnostic;
import java.util.List;

import static roj.asm.type.TypeHelper.componentType;

/**
 * 操作符 - 定义数组
 *
 * @author Roj233
 * @since 2022/2/27 19:43
 */
class ArrayDef implements ExprNode {
	IType type;
	private TypeCast.Cast[] casts;
	private final List<ExprNode> expr;
	private byte flag;

	ArrayDef(IType type, List<ExprNode> args, boolean sized) {
		this.type = type;
		this.expr = args;
		this.flag = (byte) (sized ? 1 : 0);
	}

	@Override
	public IType type() { return type; }

	@NotNull
	@Override
	public ExprNode resolve(CompileContext ctx) throws ResolveException {
		if ((flag&4) != 0) return this;
		flag |= 4;

		boolean resolved = false;
		if (type == null) {
			resolved = true;

			// TODO => should be nulltype[]
			// 这里的类型推断存在不少问题
			// 大概需要从ctx引入一些上级的信息
			ctx.report(Diagnostic.Kind.WARNING, "arrayDef.warn.notTestedPath");
			IType type1 = null;

			for (int i = 0; i < expr.size(); i++) {
				ExprNode node = expr.get(i).resolve(ctx);
				expr.set(i, node);

				if (type1 == null) type1 = node.type();
				else type1 = ctx.getCommonParent(type1, node.type());
			}

			type = type1 == null ? Asterisk.anyType : type1;
		} else {
			ctx.resolveType(type);
		}

		boolean isAllConstant = (flag&1) == 0;
		IType exprType = isAllConstant ? componentType(type) : Type.std(Type.INT);
		casts = new TypeCast.Cast[expr.size()];
		for (int i = 0; i < expr.size(); i++) {
			ExprNode node = expr.get(i);
			if (!resolved) {
				node = node.resolve(ctx);
				expr.set(i, node);
			}
			if (!node.isConstant()) isAllConstant = false;

			TypeCast.Cast cast = ctx.castTo(node.type(), exprType, TypeCast.E_NUMBER_DOWNCAST);
			if (cast.type < 0) ctx.report(Diagnostic.Kind.NOTE, "arrayDef.note.autoDownCastNumber");
			casts[i] = cast;
		}

		if (isAllConstant) flag |= 2;
		return this;
	}

	@Override
	public boolean isConstant() { return (flag&2) != 0; }

	@Override
	public Object constVal() {
		Object[] array = new Object[expr.size()];
		for (int i = 0; i < expr.size(); i++) array[i] = expr.get(i).constVal();
		return array;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);

		if ((flag&1) != 0) {
			for (int i = 0; i < expr.size(); i++) {
				expr.get(i).writeDyn(cw, casts[i]);
			}

			makeArray(cw, expr.size());
		} else {
			cw.ldc(expr.size());

			byte storeType = InsnHelper.XAStore(makeArray(cw, 1));
			for (int i = 0; i < expr.size(); i++) {
				cw.one(Opcodes.DUP);
				cw.ldc(i);
				expr.get(i).writeDyn(cw, casts[i]);
				cw.one(storeType);
			}
		}
	}

	private Type makeArray(MethodWriter cw, int dimension) {
		Type at = type.rawType();
		if (at.array() == 1) {
			if (at.isPrimitive()) cw.newArray(InsnHelper.ToPrimitiveArrayId(at.type));
			else cw.clazz(Opcodes.ANEWARRAY, at);
		} else {
			cw.multiArray(at.getActualClass(), dimension);
		}
		return at;
	}

	@Override
	public String toString() {
		CharList sb = new CharList().append("new ");
		if ((flag&1) != 0) {
			IType clone = type.clone();
			clone.setArrayDim(0);
			sb.append(clone);

			for (int i = 0; i < expr.size(); i++)
				sb.append('[').append(expr.get(i)).append(']');
			sb.padEnd("[]", (type.array()-expr.size())<<1);
			return sb.toStringAndFree();
		} else {
			sb.append(type).append(" {");
			if (!expr.isEmpty()) {
				int i = 0;
				while (true) {
					sb.append(expr.get(i++));
					if (i == expr.size()) break;
					sb.append(',');
				}
			}
			return sb.append("}").toStringAndFree();
		}
	}

	@Override
	public boolean equalTo(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		return expr.equals(((ArrayDef) o).expr);
	}
}