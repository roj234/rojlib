package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.insn.AbstractCodeWriter;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.asm.MethodWriter;
import roj.config.data.CEntry;
import roj.text.CharList;
import roj.util.DynByteBuf;

/**
 * 编译期基本类型数组
 * 目前只支持byte和1维，后续改进
 * @author Roj233
 * @since 2025/3/13 5:04
 */
public final class CompiledArray extends ExprNode {
	private final Type dataType;
	private final DynByteBuf array;

	CompiledArray(Type dataType, DynByteBuf array) {
		this.dataType = dataType;
		this.array = array;
	}

	@Override
	public String toString() {
		CharList sb = new CharList().append("new ");
		sb.append(dataType).append(" {");
		var arr = array.slice();
		if (arr.isReadable()) {
			while (true) {
				sb.append(arr.readByte());
				if (!arr.isReadable()) break;
				sb.append(',');
			}
		}
		return sb.append("}").toStringAndFree();
	}

	@Override public IType type() { return dataType; }
	@Override public boolean isConstant() {return true;}
	@Override public Object constVal() {
		var arr = array.slice();
		Object[] array = new Object[arr.readableBytes()];
		int i = 0;
		while (arr.isReadable()) {
			array[i++] = Constant.valueOf(CEntry.valueOf(arr.readByte()));
		}
		return array;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);

		var arr = array.slice();

		cw.ldc(arr.readableBytes());

		// TODO UnBase128 from ArrayUtil
		byte storeType = AbstractCodeWriter.ArrayStore(makeArray(cw, 1));
		int i = 0;
		while (arr.isReadable()) {
			cw.one(Opcodes.DUP);
			cw.ldc(i++);
			cw.ldc(arr.readByte());
			cw.one(storeType);
		}
	}
	private Type makeArray(MethodWriter cw, int dimension) {
		Type at = dataType.rawType();
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

		CompiledArray that = (CompiledArray) o;

		if (!dataType.equals(that.dataType)) return false;
		return array.equals(that.array);
	}

	@Override
	public int hashCode() {
		int result = dataType.hashCode();
		result = 31 * result + array.hashCode();
		return result;
	}
}