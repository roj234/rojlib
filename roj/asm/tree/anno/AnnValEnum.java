package roj.asm.tree.anno;

import roj.asm.cp.ConstantPool;
import roj.asm.type.TypeHelper;
import roj.text.CharList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class AnnValEnum extends AnnVal {
	public AnnValEnum(String type, String value) {
		// 当你已知这不可能是基本类型...
		this.clazz = type.substring(1, type.length()-1);
		this.value = value;
	}

	public String clazz, value;

	public AnnValEnum asEnum() { return this; }

	public byte type() { return ENUM; }

	public void toByteArray(ConstantPool cp, DynByteBuf w) { w.put((byte) ENUM).putShort(cp.getUtfId("L" + clazz + ';')).putShort(cp.getUtfId(value)); }
	public String toString() {
		CharList sb = new CharList();
		TypeHelper.toStringOptionalPackage(sb, clazz);
		return sb.replace('/', '.').append('.').append(value).toStringAndFree();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		AnnValEnum anEnum = (AnnValEnum) o;

		if (!clazz.equals(anEnum.clazz)) return false;
		return value.equals(anEnum.value);
	}

	@Override
	public int hashCode() {
		int result = clazz.hashCode();
		result = 31 * result + value.hashCode();
		return result;
	}
}