package roj.config.node;

import roj.config.ValueEmitter;

import java.util.AbstractList;

/**
 * @author Roj233
 * @since 2022/5/17 1:43
 */
public final class LongArrayValue extends ListValue {
	private final class Longs extends AbstractList<ConfigValue> {
		private final LongValue ref = new LongValue();

		public int size() { return value.length; }
		public ConfigValue get(int index) {
			ref.value = value[index];
			return ref;
		}
		public ConfigValue set(int index, ConfigValue element) {
			ref.value = value[index];
			value[index] = element.asLong();
			return ref;
		}
	}

	public long[] value;

	public LongArrayValue(long[] value) {
		super(null);
		elements = new Longs();
		this.value = value;
	}

	public long[] toLongArray() {return value.clone();}

	public void accept(ValueEmitter visitor) { visitor.emit(value); }
	public Object unwrap() { return value; }
}