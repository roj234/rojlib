package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.insn.AbstractCodeWriter;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.asm.MethodWriter;
import roj.compiler.resolve.TypeCast;
import roj.config.data.CEntry;
import roj.text.CharList;
import roj.util.DynByteBuf;

/**
 * 编译期基本类型数组
 * @author Roj233
 * @since 2025/3/13 5:04
 */
final class PrimitiveArray extends ExprNode {
	private final Type dataType;
	private final DynByteBuf array;

	PrimitiveArray(int dataType, DynByteBuf array) {
		this.dataType = Type.primitive(dataType);
		this.array = array;
	}

	@Override
	public String toString() {
		CharList sb = new CharList().append("new <compiledArray>");
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

	@Override public IType type() {return dataType;}
	@Override public boolean isConstant() {return true;}
	@Override public Object constVal() {
		var arr = array.slice();
		Object[] array = new Object[arr.readableBytes()];
		int i = 0;
		while (arr.isReadable()) {
			array[i++] = valueOf(CEntry.valueOf(arr.readByte()));
		}
		return array;
	}

	private static final byte[] COMPONENT_SIZE = {1,1,2,2,4,8,4,8,0};
	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);

		var arr = array.slice();

		// 这里没用getActualType
		int cap = TypeCast.getDataCap(dataType.type);
		cw.ldc(arr.readableBytes() / COMPONENT_SIZE[cap]);
		cw.newArray(AbstractCodeWriter.ToPrimitiveArrayId(dataType.type));

		byte storeType = AbstractCodeWriter.ArrayStore(dataType);
		int i = 0;
		while (arr.isReadable()) {
			cw.one(Opcodes.DUP);
			cw.ldc(i++);
			switch (cap) {
				case 0, 1 -> cw.ldc(arr.readByte());
				case 2 -> cw.ldc(arr.readShort());
				case 3 -> cw.ldc(arr.readChar());
				case 4 -> cw.ldc(arr.readInt());
				case 5 -> cw.ldc(arr.readLong());
				case 6 -> cw.ldc(arr.readFloat());
				case 7 -> cw.ldc(arr.readDouble());
			}
			cw.one(storeType);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		PrimitiveArray that = (PrimitiveArray) o;

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