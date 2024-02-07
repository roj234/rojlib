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
	public AnnValEnum(String type, String field) {
		this.owner = type;
		this.field = field;
	}

	/**
	 * ClassOnlyDesc
	 */
	public String owner;
	public String field;
	public String rawOwner() { return owner.substring(1, owner.length()-1); }

	public AnnValEnum asEnum() { return this; }

	public byte type() { return ENUM; }

	public void toByteArray(ConstantPool cp, DynByteBuf w) { w.put((byte) ENUM).putShort(cp.getUtfId(owner)).putShort(cp.getUtfId(field)); }
	public String toString() {
		CharList sb = new CharList();
		TypeHelper.toStringOptionalPackage(sb, rawOwner());
		return sb.replace('/', '.').append('.').append(field).toStringAndFree();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		AnnValEnum anEnum = (AnnValEnum) o;

		if (!owner.equals(anEnum.owner)) return false;
		return field.equals(anEnum.field);
	}

	@Override
	public int hashCode() {
		int result = owner.hashCode();
		result = 31 * result + field.hashCode();
		return result;
	}
}