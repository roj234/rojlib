package roj.config.node;

import roj.config.ValueEmitter;

import java.util.AbstractList;

/**
 * @author Roj233
 * @since 2022/5/17 1:43
 */
public class ByteArrayValue extends ListValue {
	private final class Bytes extends AbstractList<ConfigValue> {
		private final ByteValue ref = new ByteValue((byte) 0);

		public int size() { return value.length; }
		public ConfigValue get(int i) {
			ref.value = value[i];
			return ref;
		}
		public ConfigValue set(int i, ConfigValue e) {
			ref.value = value[i];
			value[i] = (byte) e.asInt();
			return ref;
		}
	}

	public byte[] value;

	public ByteArrayValue(byte[] value) {
		super(null);
		elements = new Bytes();
		this.value = value;
	}

	public byte[] toByteArray() {return value.clone();}

	public void accept(ValueEmitter visitor) { visitor.emit(value); }
	public Object unwrap() { return value; }
}