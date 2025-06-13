package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.insn.AbstractCodeWriter;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.CompileContext;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.GeneratorUtil;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.config.data.CEntry;
import roj.text.CharList;

import java.util.List;

import static roj.asm.type.TypeHelper.componentType;

/**
 * 定义数组
 * @author Roj233
 * @since 2022/2/27 19:43
 */
public final class NewArray extends Expr {
	IType type;
	private TypeCast.Cast[] casts;
	private final List<Expr> expr;
	private byte flag;

	NewArray(IType type, List<Expr> args, boolean argIsSize) {
		this.type = type;
		this.expr = args;
		this.flag = (byte) (argIsSize ? 1 : 0);
		if (argIsSize && args.size() != type.array()) throw new IllegalArgumentException("args.size() != type.array");
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

	public void setType(IType type) {this.type = type;}

	private static final char[] UNSIGNED_MAX = {0, 255, 65535, 65535};
	@NotNull
	@Override
	public Expr resolve(CompileContext ctx) throws ResolveException {
		if ((flag&4) != 0) return this;
		flag |= 4;

		if (type == null) {
			if (ctx.inReturn && GeneratorUtil.RETURNSTACK_TYPE.equals(ctx.method.returnType().owner))
				return new MultiReturn(expr).resolve(ctx);
			autoType(ctx);
			return this;
		} else {
			ctx.resolveType(type);
			if (type.array() == 0)
				ctx.report(this, Kind.ERROR, "arrayDef.notArray", type);
		}

		boolean failed = false;
		// useless if sized creation
		boolean isAllConstant = (flag&1) == 0;
		IType exprType = isAllConstant ? componentType(type) : Type.primitive(Type.INT);
		int dataCap = TypeCast.getDataCap(exprType.getActualType());
		casts = new TypeCast.Cast[expr.size()];
		for (int i = 0; i < expr.size(); i++) {
			var node = expr.get(i);
			if (node instanceof NewArray ad && ad.type == null) {
				if (exprType.array() == 0)
					ctx.report(this, Kind.ERROR, "arrayDef.notArray", exprType);
				ad.type = exprType;
			}
			node = node.resolve(ctx);
			expr.set(i, node);

			int castType;
			IType sourceType;
			ok: {
				if (node.isConstant()) {
					if (dataCap >= 1 && dataCap <= 3) {
						sourceType = node.minType();
						castType = TypeCast.E_EXPLICIT_CAST;
						break ok;
					}

					isAllConstant = false;
				}
				sourceType = node.type();
				castType = 0;
			}

			var cast = ctx.castTo(sourceType, exprType, castType);

			if (castType != 0 && cast.type == TypeCast.E_EXPLICIT_CAST) {
				var value = ((CEntry)node.constVal()).asInt();
				if (value >= 0 && value <= UNSIGNED_MAX[dataCap]) {
					if (exprType.getActualType() != Type.CHAR)
						ctx.report(this, Kind.INCOMPATIBLE, "arrayDef.autoCastNumber", node, exprType);
				} else {
					ctx.report(this, Kind.ERROR, "typeCast.error.-2", node.type(), exprType);
				}
			}
			else if (cast.type < 0) failed = true;

			casts[i] = cast.intern();
		}

		if (failed) return NaE.resolveFailed(this);
		if (isAllConstant) {
			flag |= 2;

			/*int iType = exprType.getActualType();
			if (iType != Type.CLASS) {
				var packedData = new ByteList();
				var dataCapacity = TypeCast.getDataCap(iType);
				for (int i = 0; i < expr.size(); i++) {
					var item = (CEntry) expr.get(i).constVal();
					switch (dataCapacity) {
						case 0, 1 -> packedData.put(item.asInt());
						case 2, 3 -> packedData.putShort(item.asInt());
						case 4 -> packedData.putInt(item.asInt());
						case 5 -> packedData.putLong(item.asLong());
						case 6 -> packedData.putFloat(item.asFloat());
						case 7 -> packedData.putDouble(item.asDouble());
					}
				}
				return new NewPackedArray(iType, packedData);
			}*/
		}
		return this;
	}
	private void autoType(CompileContext ctx) {
		flag |= 8;

		ctx.report(this, Kind.WARNING, "arrayDef.autoTypeTip");
		IType cp = null;

		for (int i = 0; i < expr.size(); i++) {
			var node = expr.get(i).resolve(ctx);
			expr.set(i, node);

			if (cp == null) cp = node.type();
			else cp = ctx.getCommonParent(cp, node.type());
		}

		if (cp != null) {
			cp = cp.clone();
			cp.setArrayDim(cp.array()+1);
			type = cp;
		}
	}

	@Override public IType type() { return type == null ? Asterisk.anyType : type; }
	@Override public boolean isConstant() { return (flag&2) != 0; }
	@Override public Object constVal() {
		Object[] array = new Object[expr.size()];
		for (int i = 0; i < expr.size(); i++) array[i] = expr.get(i).constVal();
		return array;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);

		if ((flag&1) != 0) {
			for (int i = 0; i < expr.size(); i++) {
				expr.get(i).write(cw, casts[i]);
			}

			makeArray(cw, expr.size());
		} else {
			cw.ldc(expr.size());

			byte storeType = AbstractCodeWriter.ArrayStore(makeArray(cw, 1));
			for (int i = 0; i < expr.size(); i++) {
				cw.insn(Opcodes.DUP);
				cw.ldc(i);
				expr.get(i).write(cw, casts[i]);
				cw.insn(storeType);
			}
		}
	}

	@Override
	public void write(MethodWriter cw, @Nullable TypeCast.Cast returnType) {
		if ((flag&8) != 0) {
			var ctx = CompileContext.get();
			if (returnType == null) {
				ctx.report(this, Kind.ERROR, "arrayDef.inferFailed");
				return;
			}
			flag = 0;
			type = returnType.getType1();
			resolve(ctx);
		}

		write(cw, false);
		if (returnType != null) returnType.write(cw);
	}

	private Type makeArray(MethodWriter cw, int dimension) {
		Type at = type.rawType();
		if (at.array() == 1) {
			if (at.type != Type.CLASS) cw.newArray(AbstractCodeWriter.ToPrimitiveArrayId(at.type));
			else cw.clazz(Opcodes.ANEWARRAY, at.owner);
		} else {
			cw.multiArray(at.getActualClass(), dimension);
		}
		return at;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		return expr.equals(((NewArray) o).expr);
	}

	@Override
	public int hashCode() { return expr.hashCode(); }
}