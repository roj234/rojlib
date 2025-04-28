package roj.asm.annotation;

import roj.asm.type.Type;
import roj.config.serial.CVisitor;

/**
 * @author Roj234
 * @since 2025/3/13 0013 6:44
 */
final class AClass extends AnnVal {
	public AClass(Type type) {value = type;}

	public Type value;

	public char dataType() {return ANNOTATION_CLASS;}

	@Override public void accept(CVisitor visitor) {((ToJVMAnnotation) visitor).valueClass(value.toDesc());}
	@Override public Object raw() {return value;}

	public String toString() {return value.toString().concat(".class");}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		return value.equals(((AClass) o).value);
	}

	@Override
	public int hashCode() {return value.hashCode();}
}
