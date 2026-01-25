package roj.staging.new_omc;

import roj.collect.ArrayList;
import roj.collect.BitSet;

import java.util.List;

/**
 * @author Roj234
 * @since 2026/02/06 16:07
 */
class State {
	State() {
		fieldIndex = -1;
		fields = new ArrayList<>();
	}
	State(State copy) {copyFrom(copy);}

	protected void copyFrom(State copy) {
		kind = copy.kind;
		valueAdapter = copy.valueAdapter;
		fields = copy.fields;
		fieldIndex = copy.fieldIndex;
		fieldPresent1 = copy.fieldPresent1;
		fieldPresent2 = copy.fieldPresent2;
		valueSort = copy.valueSort;
		key = copy.key;
		value = copy.value;
	}

	public static class Field {
		String name;
		TypeAdapter adapter;
		/**
		 * <pre>
		 * | Type    | Sort |
		 * |---------|------|
		 * | VOID    | 0    |
		 * | BOOLEAN | 1    |
		 * | BYTE    | 2    |
		 * | CHAR    | 3    |
		 * | SHORT   | 4    |
		 * | INT     | 5    |
		 * | LONG    | 6    |
		 * | FLOAT   | 7    |
		 * | DOUBLE  | 8    |
		 * | OBJECT  | 9    |
		 */
		int sort;

		static Field primitive(int sort) {return new Field(sort);}

		public Field(String name, int sort) {
			this.name = name;
			this.sort = sort;
		}
		public Field(String name, TypeAdapter adapter) {
			this.name = name;
			this.adapter = adapter;
			this.sort = 9;
		}
		public Field(int sort) {
			this.sort = sort;
		}

		@Override
		public String toString() {
			return "FieldInfo{" +
						   "name='" + name + '\'' +
						   ", adapter=" + adapter +
						   ", sort=" + sort +
						   '}';
		}
	}

	TypeAdapter.Kind kind;
	TypeAdapter valueAdapter;

	List<Field> fields;

	int fieldIndex;
	long fieldPresent1;
	BitSet fieldPresent2;

	int valueSort;
	ValueContainer key, value;
}
