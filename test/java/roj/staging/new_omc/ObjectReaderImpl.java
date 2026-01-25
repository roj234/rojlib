package roj.staging.new_omc;

import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.collect.BitSet;
import roj.config.ValueEmitter;
import roj.staging.new_omc.TypeAdapter.ArrayAdapter;
import roj.staging.new_omc.TypeAdapter.MapAdapter;
import roj.staging.new_omc.TypeAdapter.ObjectAdapter;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2026/02/06 12:15
 */
public class ObjectReaderImpl extends State implements ValueEmitter {
	private static final int WAIT_FOR_KEY = -1, SKIP_SUBTREE = -2;

	private final TypeAdapter root;
	private final ArrayList<State> stack = new ArrayList<>();
	private boolean finished;

	public ObjectReaderImpl(TypeAdapter root) {
		this.root = root;
		value = new ValueContainer();
		reset();
	}

	@Override
	public ValueEmitter reset() {
		valueSort = 9;
		valueAdapter = root;
		value.LValue = null;
		return this;
	}

	private void preValue(int sort) {
		if (fieldIndex == WAIT_FOR_KEY)
			throw new DataBindingException("Expecting map");
		if (fieldIndex < SKIP_SUBTREE) valueSort = sort;
	}
	private void postValue(ValueContainer value) {
		if (fieldIndex <= SKIP_SUBTREE) return;

		var prev = stack.getLast();
		if (prev == null) {finished = true;return;}

		var container = prev.value;

		switch (kind) {
			case ARRAY -> {
				var list = (ArrayAdapter) prev.valueAdapter;
				list.set(container, key, value);

				pushAdapter(list.getType(++key.IValue));
				fieldIndex++;
			}
			case MAP -> {
				var map = (MapAdapter) prev.valueAdapter;
				TypeAdapter newAdapter = map.set(container, key, value);
				if (newAdapter != null) {
					prev.valueAdapter = newAdapter;
					setTypeAdapter(prev, -1);
				}

				fieldIndex = WAIT_FOR_KEY;
			}
			case OBJECT -> {
				try {
					((ObjectAdapter) prev.valueAdapter).setField(container.LValue, fieldIndex, value);
				} catch (ClassCastException e) {
					error(value.toString(), 0);
				}

				boolean added;
				if (fieldIndex >= 64) added = fieldPresent2.add(fieldIndex - 64);
				else {
					var mask = 1L << fieldIndex;
					added = (fieldPresent1 & mask) == 0;
					fieldPresent1 |= mask;
				}
				if (!added) error(value.toString(), 1);

				fieldIndex = WAIT_FOR_KEY;
			}
		}
	}

	private void error(String foundType, int type) {
		String fieldName = kind == TypeAdapter.Kind.OBJECT ? fields.get(fieldIndex).name : "<element>";

		var prev = stack.getLast();
		var containerType = prev == null ? null : prev.value.LValue == null ? null : prev.value.LValue.getClass().getName();
		String valueTypeName = valueSort == 9 ? valueAdapter == null ? "string" : valueAdapter.toString() : Type.getName(Type.getBySort(valueSort));
		var sb = new CharList().append("Field '").append(containerType == null ? "<unknown>" : containerType).append('.').append(fieldName).append("' (type=").append(prev == null ? "<unknown>" : prev.valueAdapter).append(')');
		if (type == 0) sb.append(" expected ").append(valueTypeName).append(", but found ").append(foundType);
		else sb.append(" expected nothing (assigned), but found ").append(foundType);

		throw new DataBindingException(sb.toStringAndFree());
	}

	//region primitives
	@Override
	public void emit(boolean b) {
		preValue(1);
		switch (valueSort) {
			case 1 -> value.ZValue = b;
			case 9 -> value.LValue = b;
			default -> error("boolean", 0);
		}
		postValue(value);
	}
	@Override
	public void emit(byte i) {
		preValue(2);
		switch (valueSort) {
			case 2 -> value.BValue = i;
			case 3 -> {
				if (i != (char) i) error("byte", 0);
				value.CValue = (char) i;
			}
			case 4 -> value.SValue = i;
			case 5 -> value.IValue = i;
			case 6 -> value.JValue = i;
			case 7 -> value.FValue = i;
			case 8 -> value.DValue = i;
			case 9 -> value.LValue = i;
			default -> error("byte", 0);
		}
		postValue(value);
	}
	@Override
	public void emit(char i) {
		preValue(3);
		switch (valueSort) {
			case 3 -> value.CValue = i;
			case 5 -> value.IValue = i;
			case 6 -> value.JValue = i;
			case 7 -> value.FValue = i;
			case 8 -> value.DValue = i;
			case 9 -> value.LValue = i;
			default -> error("char", 0);
		}
		postValue(value);
	}
	@Override
	public void emit(short i) {
		preValue(4);
		switch (valueSort) {
			case 2 -> {
				if (i != (byte) i) error("short", 0);
				value.BValue = (byte) i;
			}
			case 3 -> {
				//noinspection ComparisonOfShortAndChar
				if (i != (char) i) error("short", 0);
				value.CValue = (char) i;
			}
			case 4 -> value.SValue = i;
			case 5 -> value.IValue = i;
			case 6 -> value.JValue = i;
			case 7 -> value.FValue = i;
			case 8 -> value.DValue = i;
			case 9 -> value.LValue = i;
			default -> error("short", 0);
		}
		postValue(value);
	}
	@Override
	public void emit(int i) {
		preValue(5);
		switch (valueSort) {
			case 5 -> value.IValue = i;
			case 6 -> value.JValue = i;
			case 7 -> value.FValue = i;
			case 8 -> value.DValue = i;
			case 9 -> value.LValue = i;

			case 2 -> {
				if (i != (byte) i) error("int", 0);
				value.BValue = (byte) i;
			}
			case 3 -> {
				if (i != (char) i) error("int", 0);
				value.CValue = (char) i;
			}
			case 4 -> {
				if (i != (short) i) error("int", 0);
				value.SValue = (short) i;
			}
			default -> error("int", 0);
		}
		postValue(value);
	}
	@Override
	public void emit(long i) {
		preValue(6);
		switch (valueSort) {
			case 6 -> value.JValue = i;
			case 7 -> value.FValue = i;
			case 8 -> value.DValue = i;
			case 9 -> value.LValue = i;

			case 2 -> {
				if (i != (byte) i) error("long", 0);
				value.BValue = (byte) i;
			}
			case 3 -> {
				if (i != (char) i) error("long", 0);
				value.CValue = (char) i;
			}
			case 4 -> {
				if (i != (short) i) error("long", 0);
				value.SValue = (short) i;
			}
			case 5 -> {
				if (i != (int) i) error("long", 0);
				value.IValue = (int) i;
			}
			default -> error("long", 0);
		}
		postValue(value);
	}
	@Override
	public void emit(float i) {
		preValue(7);
		switch (valueSort) {
			case 7 -> value.FValue = i;
			case 8 -> value.DValue = i;
			case 9 -> value.LValue = i;
			default -> {
				long longValue = (long) i;
				if (longValue != i) error("float", 0);
				emit(longValue);
			}
		}
		postValue(value);
	}
	@Override
	public void emit(double i) {
		preValue(8);
		switch (valueSort) {
			case 7 -> value.FValue = (float) i;
			case 8 -> value.DValue = i;
			case 9 -> value.LValue = i;
			default -> {
				long longValue = (long) i;
				if (longValue != i) error("double", 0);
				emit(longValue);
			}
		}
		postValue(value);
	}
	@Override
	public void emit(String s) {
		preValue(9);
		switch (valueSort) {
			case 3 -> {
				if (s.length() != 1) error("string", 0);
				value.CValue = s.charAt(0);
			}
			case 9 -> value.LValue = s;
			default -> error("string", 0);
		}
		postValue(value);
	}
	@Override
	public void emitNull() {
		preValue(9);
		if (valueSort != 9) error("null", 0);
		value.LValue = null;
		postValue(value);
	}

	@Override
	public boolean allowArray() {return true;}
	@Override
	public void emit(byte[] array) {
		if (kind == TypeAdapter.Kind.ARRAY && ((ArrayAdapter) valueAdapter).isPrimitiveArray(2)) {
			preValue(9);
			value.LValue = array;
			postValue(value);
		} else {
			ValueEmitter.super.emit(array);
		}
	}
	@Override
	public void emit(int[] array) {
		if (kind == TypeAdapter.Kind.ARRAY && ((ArrayAdapter) valueAdapter).isPrimitiveArray(5)) {
			preValue(9);
			value.LValue = array;
			postValue(value);
		} else {
			ValueEmitter.super.emit(array);
		}
	}
	@Override
	public void emit(long[] array) {
		if (kind == TypeAdapter.Kind.ARRAY && ((ArrayAdapter) valueAdapter).isPrimitiveArray(6)) {
			preValue(9);
			value.LValue = array;
			postValue(value);
		} else {
			ValueEmitter.super.emit(array);
		}
	}
	//endregion

	@Override public void emitMap() {emitMap(-1);}
	@Override public void emitMap(int size) {push(size);}
	private void pushAdapter(Field valType) {
		valueSort = valType.sort;
		valueAdapter = valType.adapter;
	}

	@Override
	public void emitKey(String key) {
		if (fieldIndex <= SKIP_SUBTREE) return;

		if (kind == TypeAdapter.Kind.MAP) {
			fields.clear();
			((MapAdapter) stack.getLast().valueAdapter).getTypes(key, fields);

			var value = this.value;

			if (this.key == null) this.key = new ValueContainer();
			this.value = this.key;
			var keyType = fields.get(0);
			fieldIndex = SKIP_SUBTREE;
			valueSort = keyType.sort;
			// 也许以后不会这样，但现在只允许指定类型的key
			assert keyType.adapter == null;
			emit(key);

			this.value = value;
			fieldIndex = 0;
			pushAdapter(fields.get(1));
		} else {
			if (kind != TypeAdapter.Kind.OBJECT) error("key(string)", 0);

			int i = ((FieldSet) fields).indexOf(key);
			if (i < 0) {
				fieldIndex = SKIP_SUBTREE;
			} else {
				fieldIndex = i;
				pushAdapter(fields.get(i));
			}
		}
	}

	@Override
	public void emitKey(int key) {
		if (fieldIndex <= SKIP_SUBTREE) return;

		if (kind == TypeAdapter.Kind.MAP) {
			fields.clear();
			((MapAdapter) stack.getLast().valueAdapter).getTypes(key, fields);

			var value = this.value;

			if (this.key == null) this.key = new ValueContainer();
			this.value = this.key;
			var keyType = fields.get(0);
			fieldIndex = SKIP_SUBTREE;
			valueSort = keyType.sort;
			// 也许以后不会这样，但现在只允许指定类型的key
			assert keyType.adapter == null;
			emit(key);

			this.value = value;
			fieldIndex = 0;
			pushAdapter(fields.get(1));
		} else {
			if (kind != TypeAdapter.Kind.OBJECT) error("key(int)", 0);
			pushAdapter(fields.get(key));
		}
	}

	@Override public void emitList() {emitList(-1);}
	@Override public void emitList(int size) {
		if (push(size)) return;
		if (kind != TypeAdapter.Kind.ARRAY) error("list", 0);
		pushAdapter(((ArrayAdapter) valueAdapter).getType(0));
	}

	private boolean push(int size) {
		if (fieldIndex <= SKIP_SUBTREE) {
			fieldIndex--;
			return true;
		}

		if (valueAdapter == null) error("<compound type (map/array)>", 0);

		State prev = new State(this);
		stack.add(prev);
		setTypeAdapter(prev, size);
		value = new ValueContainer();
		return false;
	}

	private void setTypeAdapter(State prev, int size) {
		prev.value.LValue = prev.valueAdapter.newContainerBuilder(size);
		kind = prev.valueAdapter.kind();
		fieldIndex = kind == TypeAdapter.Kind.ARRAY ? 0 : -1;
		fieldPresent1 = 0;

		if (kind != TypeAdapter.Kind.OBJECT) {
			key = new ValueContainer();
			if (kind == TypeAdapter.Kind.MAP) {
				fields = new ArrayList<>(2);
			}
		} else {
			key = null;
			fields = ((TypeAdapter.ObjectAdapter) prev.valueAdapter).listFields();
			fieldPresent2 = fields.size() > 64 ? new BitSet(fields.size() - 64) : null;
		}
	}

	@Override
	public void pop() {
		if (fieldIndex <= SKIP_SUBTREE) {
			fieldIndex++;
			return;
		}

		var prev = stack.pop();
		if (prev == null) return;

		switch (kind) {
			case MAP -> {
				if (fieldIndex > 0) throw new DataBindingException("Missing value (state="+fieldIndex+")");
			}
			case OBJECT -> {
				if (fieldIndex != -1) error("<missing>", 0);

				long mask = ((ObjectAdapter) prev.valueAdapter).maskOptionalFields(fieldPresent1, fieldPresent2);
				int fieldCount = Long.bitCount(mask) + (fieldPresent2 == null ? 0 : fieldPresent2.size());
				if (fieldCount < fields.size()) {
					var missing = new CharList();
					Object lValue = prev.value.LValue;
					missing.append("Missing some field for '").append(lValue == null ? "<unknown>" : lValue.getClass().getName()).append("': ");
					for (int i = 0; i < fields.size(); i++) {
						Field field = fields.get(i);

						boolean exist = i < 64 ? (mask & (1L << i)) != 0 : fieldPresent2.contains(i - 64);
						if (!exist) missing.append(field.name).append(',');
					}

					throw new DataBindingException(missing.toStringAndFree());
				}
			}
		}

		var vc = prev.value;
		if (prev.valueAdapter != null)
			vc.LValue = prev.valueAdapter.build(vc.LValue, value);

		copyFrom(prev);
		postValue(vc);
	}
}
