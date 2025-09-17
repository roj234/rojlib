package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.Opcodes;
import roj.asm.insn.AbstractCodeWriter;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.asm.MethodWriter;
import roj.compiler.resolve.TypeCast;
import roj.config.node.ConfigValue;
import roj.text.CharList;
import roj.util.DynByteBuf;

/**
 * 编译期基本类型数组
 * @author Roj233
 * @since 2025/3/13 5:04
 */
final class NewPackedArray extends Expr {
	private final Type dataType;
	private final DynByteBuf elements;

	NewPackedArray(int dataType, DynByteBuf elements) {
		this.dataType = Type.primitive(dataType);
		this.elements = elements;
	}

	@Override
	public String toString() {
		CharList sb = new CharList().append("new <compiledArray>");
		sb.append(dataType).append(" {");
		var arr = elements.slice();
		int sort = Type.getSort(dataType.type);
		if (arr.isReadable()) {
			while (true) {
				sb.append(switch (sort) {
					default -> arr.readBoolean();
					case Type.SORT_BYTE -> arr.readByte();
					case Type.SORT_SHORT -> arr.readShort();
					case Type.SORT_CHAR -> arr.readChar();
					case Type.SORT_INT -> arr.readInt();
					case Type.SORT_LONG -> arr.readLong();
					case Type.SORT_FLOAT -> arr.readFloat();
					case Type.SORT_DOUBLE -> arr.readDouble();
				});
				if (!arr.isReadable()) break;
				sb.append(',');
			}
		}
		return sb.append('}').toStringAndFree();
	}

	@Override public IType type() {return dataType;}
	@Override public boolean isConstant() {return true;}
	@Override public Object constVal() {
		var arr = elements.slice();
		int sort = Type.getSort(dataType.type);
		Object[] array = new Object[arr.readableBytes() / COMPONENT_SIZE[sort]];
		int i = 0;
		while (arr.isReadable()) {
			array[i++] = switch (sort) {
				default -> ConfigValue.valueOf(arr.readBoolean());
				case Type.SORT_BYTE -> ConfigValue.valueOf(arr.readByte());
				case Type.SORT_SHORT -> ConfigValue.valueOf(arr.readShort());
				case Type.SORT_CHAR -> ConfigValue.valueOf(arr.readChar());
				case Type.SORT_INT -> ConfigValue.valueOf(arr.readInt());
				case Type.SORT_LONG -> ConfigValue.valueOf(arr.readLong());
				case Type.SORT_FLOAT -> ConfigValue.valueOf(arr.readFloat());
				case Type.SORT_DOUBLE -> ConfigValue.valueOf(arr.readDouble());
			};
		}
		return array;
	}

	private static final byte[] COMPONENT_SIZE = {0,1,1,2,2,4,8,4,8,0};
	@Override
	protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) {
		mustBeStatement(cast);

		var arr = elements.slice();

		// 这里没用getActualType
		int cap = Type.getSort(dataType.type);
		cw.ldc(arr.readableBytes() / COMPONENT_SIZE[cap]);
		cw.newArray(AbstractCodeWriter.ToPrimitiveArrayId(dataType.type));

		byte storeType = AbstractCodeWriter.ArrayStore(dataType);
		int i = 0;
		while (arr.isReadable()) {
			cw.insn(Opcodes.DUP);
			cw.ldc(i++);
			switch (cap) {
				default -> cw.ldc(arr.readByte());
				case Type.SORT_SHORT -> cw.ldc(arr.readShort());
				case Type.SORT_CHAR -> cw.ldc(arr.readChar());
				case Type.SORT_INT -> cw.ldc(arr.readInt());
				case Type.SORT_LONG -> cw.ldc(arr.readLong());
				case Type.SORT_FLOAT -> cw.ldc(arr.readFloat());
				case Type.SORT_DOUBLE -> cw.ldc(arr.readDouble());
			}
			cw.insn(storeType);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		NewPackedArray that = (NewPackedArray) o;

		if (!dataType.equals(that.dataType)) return false;
		return elements.equals(that.elements);
	}

	@Override
	public int hashCode() {
		int result = dataType.hashCode();
		result = 31 * result + elements.hashCode();
		return result;
	}
}