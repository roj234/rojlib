package roj.config.data;

import roj.config.serial.CVisitor;

import java.util.AbstractList;

/**
 * @author Roj233
 * @since 2022/5/17 1:43
 */
public final class CLongArray extends CList {
	private final class Longs extends AbstractList<CEntry> {
		private final CLong ref = new CLong();

		public int size() { return value.length; }
		public CEntry get(int index) {
			ref.value = value[index];
			return ref;
		}
		public CEntry set(int index, CEntry element) {
			ref.value = value[index];
			value[index] = element.asLong();
			return ref;
		}
	}

	public long[] value;

	public CLongArray(long[] value) {
		super(null);
		elements = new Longs();
		this.value = value;
	}

	public long[] toLongArray() {return value.clone();}

	public void accept(CVisitor visitor) { visitor.value(value); }
	public Object unwrap() { return value; }
}