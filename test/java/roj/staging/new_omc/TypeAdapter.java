package roj.staging.new_omc;

import roj.collect.BitSet;

import java.util.List;

/**
 * @author Roj234
 * @since 2026/02/06 16:09
 */
public interface TypeAdapter {
	enum Kind { ARRAY, MAP, OBJECT, POLYMERIC }

	Kind kind();
	Object newContainerBuilder(int size);
	default Object build(Object container, ValueContainer value) {return container;}

	interface ObjectAdapter extends TypeAdapter {
		default Kind kind() { return Kind.OBJECT; }

		FieldSet listFields();
		long maskOptionalFields(long fieldPresent1, BitSet fieldPresent2);
		void setField(Object container, int fieldId, ValueContainer value);
	}

	interface ContainerAdapter extends TypeAdapter {
		TypeAdapter set(ValueContainer container, ValueContainer key, ValueContainer value);
	}

	interface ArrayAdapter extends ContainerAdapter {
		default Kind kind() { return Kind.ARRAY; }

		boolean isPrimitiveArray(int sort);
		State.Field getType(int index);
	}

	interface MapAdapter extends ContainerAdapter {
		default Kind kind() { return Kind.MAP; }

		default void getTypes(int key, List<State.Field> fields) {getTypes(String.valueOf(key), fields);}
		void getTypes(String key, List<State.Field> fields);
	}
}
