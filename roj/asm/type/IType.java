package roj.asm.type;

import roj.text.CharList;

import java.util.function.UnaryOperator;

/**
 * @author solo6975
 * @since 2021/9/4 19:03
 */
public interface IType {
	byte STANDARD_TYPE = 0, GENERIC_TYPE = 1, TYPE_PARAMETER_TYPE = 2, EMPTY_TYPE = 3, GENERIC_SUBCLASS_TYPE = 4, ANY_TYPE = 5;
	byte genericType();

	default String toDesc() {
		CharList sb = new CharList();
		toDesc(sb);
		return sb.toString();
	}
	void toDesc(CharList sb);
	void toString(CharList sb);

	byte TYPE_PARAMETER_ENV = 0, FIELD_ENV = 1, INPUT_ENV = 2, OUTPUT_ENV = 3, THROW_ENV = 4, GENERIC_ENV = 5;
	void checkPosition(int env, int pos);

	default Type rawType() {
		throw new UnsupportedOperationException();
	}
	default int array() {
		return 0;
	}
	default void setArrayDim(int array) {
		throw new UnsupportedOperationException();
	}

	default String owner() {
		throw new UnsupportedOperationException();
	}
	default void owner(String owner) {
		throw new UnsupportedOperationException();
	}

	default void rename(UnaryOperator<String> fn) {}
}
