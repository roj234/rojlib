package roj.asm.tree.anno;

import roj.asm.cst.ConstantPool;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class AnnValEnum extends AnnVal {
	public AnnValEnum(String type, String value) {
		// 当你已知这不可能是基本类型...
		this.clazz = type.substring(1, type.length() - 1);
		this.value = value;
	}

	public String clazz, value;

	@Override
	public AnnValEnum asEnum() {
		return this;
	}

	public void toByteArray(ConstantPool pool, DynByteBuf w) {
		w.put((byte) ENUM).putShort(pool.getUtfId("L" + this.clazz + ';')).putShort(pool.getUtfId(value));
	}

	public String toString() {
		return String.valueOf(clazz) + '.' + value;
	}

	@Override
	public byte type() {
		return ENUM;
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