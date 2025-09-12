package roj.config.node;

import roj.config.ValueEmitter;

import java.util.AbstractList;

/**
 * @author Roj233
 * @since 2022/5/17 1:43
 */
public class IntArrayValue extends ListValue {
	private final class Ints extends AbstractList<ConfigValue> {
		//    重复使用一个对象会出问题么
		//    Immutable TByte ?
		//    出了问题再说吧
		private final IntValue ref = new IntValue(0);

		public int size() { return value.length; }
		public ConfigValue get(int i) {
			ref.value = value[i];
			return ref;
		}
		public ConfigValue set(int i, ConfigValue e) {
			ref.value = value[i];
			value[i] = e.asInt();
			return ref;
		}
	}

	public int[] value;

	public IntArrayValue(int[] value) {
		super(null);
		elements = new Ints();
		this.value = value;
	}

	public int[] toIntArray() {return value.clone();}

	public void accept(ValueEmitter visitor) { visitor.emit(value); }
	public Object unwrap() { return value; }
}