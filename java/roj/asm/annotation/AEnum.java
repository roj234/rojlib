package roj.asm.annotation;

import roj.asm.MemberDescriptor;
import roj.asm.type.TypeHelper;
import roj.config.serial.CVisitor;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2025/3/13 6:44
 */
final class AEnum extends AnnVal {
	public AEnum(String type, String field) {
		this.owner = type;
		this.field = field;
	}

	private String owner;
	public String field;

	public char dataType() {return ENUM;}

	public String owner() {
		if (owner.endsWith(";")) owner = owner.substring(1, owner.length() - 1);
		return owner;
	}

	public void setOwner(String owner) {this.owner = owner;}

	@Override public void accept(CVisitor visitor) {((ToJVMAnnotation) visitor).valueEnum(owner, field);}

	@Override public Object raw() {return new MemberDescriptor(owner(), field, owner);}

	public String toString() {
		CharList sb = new CharList();
		TypeHelper.toStringOptionalPackage(sb, owner());
		return sb.replace('/', '.').append('.').append(field).toStringAndFree();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		AEnum anEnum = (AEnum) o;

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
