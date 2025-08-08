package roj.config.data;

import roj.config.serial.CVisitor;

import java.util.AbstractList;

/**
 * @author Roj233
 * @since 2022/5/17 1:43
 */
public class CIntArray extends CList {
	private final class Ints extends AbstractList<CEntry> {
		//    重复使用一个对象会出问题么
		//    Immutable TByte ?
		//    出了问题再说吧
		private final CInt ref = new CInt(0);

		public int size() { return value.length; }
		public CEntry get(int i) {
			ref.value = value[i];
			return ref;
		}
		public CEntry set(int i, CEntry e) {
			ref.value = value[i];
			value[i] = e.asInt();
			return ref;
		}
	}

	public int[] value;

	public CIntArray(int[] value) {
		super(null);
		elements = new Ints();
		this.value = value;
	}

	public int[] toIntArray() {return value.clone();}

	public void accept(CVisitor visitor) { visitor.value(value); }
	public Object unwrap() { return value; }
}