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

	private String owner;
	public String field;

	public String owner() {
		if (owner.endsWith(";")) owner = owner.substring(1, owner.length()-1);
		return owner;
	}
	public void setOwner(String owner) { this.owner = owner; }

	public AnnValEnum asEnum() { return this; }

	public byte type() { return ENUM; }

	public void toByteArray(ConstantPool cp, DynByteBuf w) {
		int id;
		if (owner.endsWith(";")) {
			id = cp.getUtfId(owner);
		} else {
			CharList sb = new CharList().append('L').append(owner).append(';');
			id = cp.getUtfId(sb);
			sb._free();
		}
		w.put(ENUM).putShort(id).putShort(cp.getUtfId(field));
	}
	public String toString() {
		CharList sb = new CharList();
		TypeHelper.toStringOptionalPackage(sb, owner());
		return sb.replace('/', '.').append('.').append(field).toStringAndFree();
	}
	@Override
	public String toRawString() {return field;}

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