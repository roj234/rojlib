package roj.asm.annotation;

import roj.asm.type.Type;
import roj.config.ValueEmitter;

/**
 * @author Roj234
 * @since 2025/3/13 6:44
 */
final class TypeVal extends AnnVal {
	public TypeVal(Type type) {value = type;}

	public Type value;

	public char dataType() {return ANNOTATION_CLASS;}

	@Override public void accept(ValueEmitter visitor) {((AnnotationEncoder) visitor).emitType(value.toDesc());}
	@Override public Object raw() {return value;}

	public String toString() {return value.toString().concat(".class");}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		return value.equals(((TypeVal) o).value);
	}

	@Override
	public int hashCode() {return value.hashCode();}
}
