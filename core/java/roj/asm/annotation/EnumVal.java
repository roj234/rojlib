package roj.asm.annotation;

import roj.asm.MemberDescriptor;
import roj.asm.type.TypeHelper;
import roj.config.ValueEmitter;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2025/3/13 6:44
 */
final class EnumVal extends AnnVal {
	public EnumVal(String type, String field) {
		this.type = type;
		this.field = field;
	}

	private String type;
	public String field;

	public char dataType() {return ENUM;}

	public String type() {
		if (type.endsWith(";")) type = type.substring(1, type.length() - 1);
		return type;
	}

	@Override public void accept(ValueEmitter visitor) {((AnnotationEncoder) visitor).emitEnum(type, field);}

	@Override public Object raw() {return new MemberDescriptor(type(), field, "L"+type()+";");}

	public String toString() {
		CharList sb = new CharList();
		TypeHelper.toStringOptionalPackage(sb, type());
		return sb.replace('/', '.').append('.').append(field).toStringAndFree();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		EnumVal anEnum = (EnumVal) o;

		if (!type().equals(anEnum.type())) return false;
		return field.equals(anEnum.field);
	}

	@Override
	public int hashCode() {
		int result = type().hashCode();
		result = 31 * result + field.hashCode();
		return result;
	}
}
