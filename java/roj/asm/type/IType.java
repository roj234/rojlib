package roj.asm.type;

import org.jetbrains.annotations.Contract;
import roj.text.CharList;

import java.util.function.UnaryOperator;

/**
 * @author solo6975
 * @since 2021/9/4 19:03
 */
public interface IType extends java.lang.reflect.Type, Cloneable {
	byte STANDARD_TYPE = 0, GENERIC_TYPE = 1, TYPE_PARAMETER_TYPE = 2, ANY_TYPE = 3, PLACEHOLDER_TYPE = 4, GENERIC_SUBCLASS_TYPE = 5,
	ASTERISK_TYPE = 6, CONCRETE_ASTERISK_TYPE = 7;
	@Contract(pure = true)
	byte genericType();

	default String toDesc() {
		CharList sb = new CharList();
		toDesc(sb);
		return sb.toStringAndFree();
	}
	@Contract(pure = true)
	void toDesc(CharList sb);
	@Contract(pure = true)
	void toString(CharList sb);

	byte TYPE_PARAMETER_ENV = 0, FIELD_ENV = 1, INPUT_ENV = 2, OUTPUT_ENV = 3, THROW_ENV = 4, GENERIC_ENV = 5;
	void checkPosition(int env, int pos);

	@Contract(pure = true)
	default boolean isPrimitive() { return false; }
	@Contract(pure = true)
	default int getActualType() { return Type.CLASS; }

	@Contract(pure = true)
	default Type rawType() { throw new UnsupportedOperationException(getClass().getName()); }
	@Contract(pure = true)
	default int array() { return 0; }
	default void setArrayDim(int array) { throw new UnsupportedOperationException(getClass().getName()); }

	@Contract(pure = true)
	default String owner() { throw new UnsupportedOperationException(getClass().getName()); }
	default void owner(String owner) { throw new UnsupportedOperationException(getClass().getName()); }

	default void rename(UnaryOperator<String> fn) {}

	IType clone();
}