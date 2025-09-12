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
		int cap = TypeCast.getDataCap(dataType.type);
		if (arr.isReadable()) {
			while (true) {
				sb.append( switch (cap) {
					// 0 or 1
					default -> arr.readByte();
					case 2 -> arr.readShort();
					case 3 -> arr.readChar();
					case 4 -> arr.readInt();
					case 5 -> arr.readLong();
					case 6 -> arr.readFloat();
					case 7 -> arr.readDouble();
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
		int cap = TypeCast.getDataCap(dataType.type);
		Object[] array = new Object[arr.readableBytes() / switch (cap) {
			// 0 or 1
			default   -> 1;
			case 2, 3 -> 2;
			case 4, 6 -> 4;
			case 5, 7 -> 8;
		}];
		int i = 0;
		while (arr.isReadable()) {
			array[i++] = switch (cap) {
				// 0 or 1
				default -> ConfigValue.valueOf(arr.readByte());
				case 2 -> ConfigValue.valueOf(arr.readShort());
				case 3 -> ConfigValue.valueOf(arr.readChar());
				case 4 -> ConfigValue.valueOf(arr.readInt());
				case 5 -> ConfigValue.valueOf(arr.readLong());
				case 6 -> ConfigValue.valueOf(arr.readFloat());
				case 7 -> ConfigValue.valueOf(arr.readDouble());
			};
		}
		return array;
	}

	private static final byte[] COMPONENT_SIZE = {1,1,2,2,4,8,4,8,0};
	@Override
	protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) {
		mustBeStatement(cast);

		var arr = elements.slice();

		// 这里没用getActualType
		int cap = TypeCast.getDataCap(dataType.type);
		cw.ldc(arr.readableBytes() / COMPONENT_SIZE[cap]);
		cw.newArray(AbstractCodeWriter.ToPrimitiveArrayId(dataType.type));

		byte storeType = AbstractCodeWriter.ArrayStore(dataType);
		int i = 0;
		while (arr.isReadable()) {
			cw.insn(Opcodes.DUP);
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